# RFC 0001: Arquitetura nodal DC 0.3

- Status: proposta implementável
- Versão alvo: 0.3
- Card: CRL-22
- Base analisada: `1d6e917`
- Última atualização: 2026-09-17

## Resumo

A versão 0.3 substituirá o solver de caminho único por uma representação explícita
de terminais, nós equipotenciais e ramos. O circuito DC será resolvido no servidor
por Modified Nodal Analysis (MNA), com componentes lineares estampados diretamente
e LEDs tratados por um modelo linear por partes com iteração limitada.

A descoberta física continuará limitada a 1.024 blocos e sem carregar chunks. Fios
ideais e contatos entre faces serão colapsados em nós; resistores, chaves, LEDs e
fontes produzirão ramos entre esses nós. O resultado deixará de ser um único valor
global e passará a conter tensão por nó, corrente e potência por ramo, diagnósticos
estruturados e validade local.

Esta RFC define contratos e sequência de migração. Ela não implementa o novo
solver.

## Motivação

O código atual usa cada bloco como um vértice de `CircuitGraph`. As conexões de
`BlockTwoTerminal` entram no mesmo vértice, portanto não há como representar os
dois potenciais do componente. `SimpleCircuitSolver` exige grau 1 nas extremidades
e grau 2 no restante, ordena um caminho da fonte ao GND e retorna um único
`CircuitResult`. Esse modelo é correto para o MVP em série, mas não pode expressar:

- dois terminais distintos no mesmo resistor ou LED;
- nós com três ou mais ramos;
- tensão entre duas pontas do multímetro;
- correntes diferentes em ramos paralelos;
- mais de uma carga, fonte ou LED;
- curto limitado pela fonte;
- diagnóstico localizado de nó flutuante ou componente excedido.

Estender `SimpleCircuitSolver` com casos especiais preservaria a ambiguidade e
criaria física falsa. A fronteira correta é extrair primeiro uma netlist nodal e
então resolver suas equações.

## Escopo

### Incluído na primeira entrega 0.3

- DC em regime permanente;
- resistores em série, paralelo e combinações série-paralelo;
- múltiplos resistores, LEDs, chaves e cargas na mesma rede;
- uma ou mais fontes de tensão com resistência interna explícita;
- fios ideais, GND comum e componentes abertos;
- tensão por nó, corrente e potência por ramo;
- curto-circuito limitado pela resistência interna da fonte;
- LED com polaridade, queda direta e resistência dinâmica simplificadas;
- diagnósticos para ausência de referência, nó flutuante, singularidade,
  não convergência, curto e limites excedidos;
- base elétrica para fusível/disjuntor, diodo, potenciômetro e chave de alavanca;
- cache e invalidação servidor-autoritativos, conscientes de chunks.

### Fora de escopo

- análise transiente;
- capacitores e indutores;
- corrente alternada e fasores;
- semicondutores exponenciais ou dependentes de temperatura;
- transistores e amplificadores operacionais;
- fios com resistência distribuída;
- simulação eletromagnética;
- solução parcial através de chunks descarregados;
- execução do solver no cliente.

Esses itens devem receber modelos próprios. Não serão aproximados como resistores
sem que uma RFC futura documente a simplificação.

## Decisões normativas

Os termos `DEVE`, `NÃO DEVE` e `PODE` indicam requisitos da implementação.

1. A topologia usada pelo solver DEVE ser independente de classes Forge.
2. Um terminal físico DEVE ser identificado por posição e face, não apenas pelo
   bloco.
3. Contatos ideais e o interior de fios DEVEM ser colapsados antes da montagem da
   matriz.
4. Componentes DEVEM contribuir com ramos orientados e stamps explícitos.
5. GND DEVE ser a única referência de 0 V da rede.
6. A fonte de 5 V DEVE ter resistência interna finita na versão 0.3.
7. O solver DEVE usar limites de trabalho fixos; tempo de parede não pode alterar
   o resultado elétrico.
8. Ordem de descoberta, `HashMap` e ordem de carregamento de chunks NÃO DEVEM
   alterar IDs, pivôs, diagnósticos nem valores além da tolerância publicada.
9. Valores indisponíveis NÃO DEVEM ser representados como zero. Sua ausência e o
   motivo devem fazer parte do resultado.
10. Nenhuma consulta ou descoberta DEVE forçar o carregamento de chunk.

## Vocabulário

### Terminal

Ponto elétrico exposto por uma face de um bloco. No conjunto atual há no máximo
um terminal por face. O identificador lógico é:

```text
TerminalId = (BlockPosition, face, ordinal)
```

`ordinal` começa em zero e permite componentes futuros com mais de um terminal
na mesma face. `face` usa uma enumeração própria do núcleo, com conversão para
`ForgeDirection` somente no adaptador de mundo.

### Contato

União ideal entre terminais de blocos adjacentes em faces opostas. Um contato só
existe quando os dois blocos o aceitam. A regra atual de
`IElectricalBlock.canConnectOnSide` continua sendo a fonte de verdade durante a
migração, mas será adaptada para descritores de terminal.

### Nó

Classe de equivalência de terminais ligados apenas por contatos e condutores
ideais. Todos os terminais de um nó têm o mesmo potencial. Um nó não é um bloco:
um resistor normalmente toca dois nós, enquanto um fio de seis conexões pertence
a um único nó.

### Ramo

Elemento orientado entre dois nós. A corrente positiva flui do terminal `a` para
o terminal `b`. Um componente pode produzir mais de um elemento MNA e nós internos,
mas expõe um único `BranchId` estável para cada ramo mensurável.

### Rede física e circuito solucionável

A rede física é o conjunto de blocos alcançável por contatos, mesmo quando uma
chave está aberta. O circuito solucionável é a netlist obtida após considerar o
estado dos ramos. Uma rede física pode conter ilhas condutivas flutuantes.

## Identidade e ordenação

`BlockPosition` continua sendo a coordenada base dentro de um
`ElectricalNetworkManager`, que já é específico de um `World`. Comparações DEVEM
usar inteiros em ordem `x`, `y`, `z`; a comparação de `toString()` usada hoje em
`ElectricalNetworkManager.solve` deve ser removida.

A ordem canônica completa será:

1. `BlockPosition` por `x`, `y`, `z` numéricos;
2. face pela ordem fixa `DOWN`, `UP`, `NORTH`, `SOUTH`, `WEST`, `EAST`;
3. ordinal;
4. tipo e índice do ramo interno.

O representante de uma classe union-find será sempre o menor `TerminalId`, sem
union by hash. `NodeId` será derivado do menor terminal do nó. O nó de referência
terá o ID reservado `REFERENCE`; nós internos usarão o `BranchId` que os criou.

`BranchId` será `(BlockPosition, componentKind, branchOrdinal)`. IDs são dados de
execução e não serão persistidos em NBT. A posição e o estado do bloco continuam
sendo a identidade persistente.

## Extração da topologia

A extração ocorrerá em quatro passos puros e limitados.

### 1. Snapshot do mundo

Na thread do servidor, o adaptador lê cada posição devolvida por
`WorldElectricalNetworkFinder` e produz um descritor imutável, sem referências a
`World`, `Block` ou `TileEntity`:

```text
ComponentSnapshot
  position
  kind
  terminals: TerminalSnapshot[]
  parameters: valores validados
  state: aberto, queimado, orientação etc.
```

Metadados e NBT são validados nesse ponto. Valores não finitos, resistência
negativa ou faces inválidas produzem `INVALID_COMPONENT_DATA` e a rede não é
resolvida.

### 2. Contatos físicos

Para cada terminal, o extrator consulta somente a posição adjacente já carregada.
Quando há terminal compatível na face oposta, cria uma união ideal. Cada contato
é processado apenas quando o primeiro `TerminalId` é menor que o segundo.

Uma face que aponta para chunk descarregado não é tratada como circuito aberto.
Ela produz `INCOMPLETE_NETWORK` e invalida todas as grandezas dependentes daquela
rede até o chunk ser carregado.

### 3. Condutores internos

- `BlockElectricalWire`: todos os seus terminais fisicamente conectados são
  unidos entre si. Um fio sem vizinhos ainda possui um grupo condutor, mas não
  precisa materializar seis terminais ausentes.
- `BlockGround`: seu terminal é unido ao nó reservado `REFERENCE`.
- `BlockResistor`, `BlockElectricalButton` e `BlockLed`: seus dois terminais
  permanecem separados e geram um ramo.
- `BlockPowerSource`: o terminal positivo permanece separado e a fonte gera um
  modelo interno ligado à referência.

O fato de uma chave estar aberta não altera a rede física nem os contatos; apenas
remove sua condutância da netlist. Assim, fechar a chave exige nova solução, mas
não muda a definição dos terminais.

### 4. Materialização

Depois das uniões, os representantes são ordenados e convertidos em `NodeId`.
Cada componente emite seus ramos em ordem canônica. A saída proposta é
`NodalCircuit`, contendo:

```text
nodes
branches
terminalToNode
componentToBranches
worldPositions
topologyFingerprint
```

`NodalCircuit` e todos os tipos em `br.com.craftonica.electrical.nodal` não
dependem de Forge.

## Referência e GND

Uma rede válida para tensões absolutas DEVE conter ao menos um `BlockGround`.
Todos os terminais GND dentro da mesma rede física são unidos a `REFERENCE` e têm
0 V exatos. Vários GND são permitidos; eles não criam ramos nem correntes
individualmente mensuráveis.

Sem GND, o extrator retorna `MISSING_REFERENCE`. O solver não escolhe
arbitrariamente um nó como zero e não publica tensões absolutas. Isso preserva a
regra educacional de que o retorno deve ser montado no mundo.

O terminal negativo interno de cada `BlockPowerSource` é ligado a `REFERENCE`,
mas não substitui a presença física de GND. Essa ligação só é habilitada depois
que um GND válido foi encontrado.

## Formulação MNA

Para `n` nós não referência e `m` fontes ideais internas, o vetor de incógnitas é:

```text
x = [V1 ... Vn J1 ... Jm]^T
```

`Vi` é a tensão do nó em relação a GND. `Jk` é a corrente da fonte ideal no
sentido do seu terminal positivo para o negativo. O sistema é:

```text
A x = z
```

A matriz é remontada a cada solução. A topologia e o plano de stamps podem ser
cacheados, mas valores mutáveis, lado direito e solução anterior não podem vazar
entre redes.

### Resistor

Para resistência `R > 0`, `g = 1 / R`, entre nós `a` e `b`:

```text
A[a,a] += g
A[b,b] += g
A[a,b] -= g
A[b,a] -= g
```

Linhas ou colunas de `REFERENCE` são omitidas. Depois da solução:

```text
I(a -> b) = (Va - Vb) / R
P_absorvida = (Va - Vb) * I
```

### Fonte ideal de tensão

Para uma fonte `E` entre `p` e `n`, com índice de corrente `k`:

```text
A[p,k] += 1
A[n,k] -= 1
A[k,p] += 1
A[k,n] -= 1
z[k]   += E
```

As referências são omitidas como no resistor. A corrente `Jk` é positiva de `p`
para `n`.

### Fonte de 5 V com resistência interna

`BlockPowerSource` será um equivalente de Thévenin:

```text
REFERENCE -- fonte ideal 5 V -- nó interno -- 10 ohms -- terminal positivo
```

Os valores iniciais normativos são `5.0 V` e `10.0 ohms`. Portanto, um curto
direto é finito e vale `0.5 A`. A resistência interna participa dos cálculos e
deve aparecer separadamente no detalhamento, sem ser fingida como resistor
colocado pelo aluno.

O nó interno e a incógnita de corrente são privados do ramo da fonte. A corrente
entregue ao circuito é positiva do nó interno para o terminal positivo. Múltiplas
fontes são permitidas; seus efeitos resultam das mesmas equações, sem escolha de
uma fonte "principal".

### Chave

Uma chave aberta não estampa condutância. Uma chave fechada usa resistência de
contato documentada de `0.01 ohm`. Esse valor evita laços singulares de fontes de
0 V, mantém corrente e potência locais definidas e representa uma simplificação
física explícita. A interface pode arredondar sua queda para zero, mas o solver
não a remove.

### LED

O LED usa um modelo linear por partes:

```text
OFF: sem condutância
ON:  I(anodo -> catodo) = (Vak - Vf) / Rd
Vf = 2.0 V
Rd = 1.0 ohm
```

No estado `ON`, `g = 1 / Rd`; o resistor e a fonte Norton equivalente estampam:

```text
A[a,a] += g                 z[a] += g * Vf
A[c,c] += g                 z[c] -= g * Vf
A[a,c] -= g
A[c,a] -= g
```

Um LED queimado equivale a ramo aberto. A persistência em `TileEntityLed`
continua sendo autoridade para esse estado.

O conjunto ativo é resolvido assim:

1. começar com todos os LEDs funcionais em `OFF`;
2. montar e resolver o sistema;
3. ligar um LED `OFF` quando `Vak > Vf + 1e-9 V`;
4. desligar um LED `ON` quando sua corrente for menor que `-1e-12 A`;
5. repetir até o conjunto não mudar, no máximo 16 iterações.

Empates permanecem `OFF`. LEDs são atualizados em ordem de `BranchId`. Se o
conjunto não estabilizar, o resultado é `NON_CONVERGENT` e não contém grandezas
locais calculadas na última tentativa.

Esse não é um modelo exponencial. `Vf`, `Rd`, limiar recomendado e limiar de
queima devem ser apresentados como parâmetros simplificados no manual.

### Componentes seguintes

| Componente | Modelo nodal previsto |
| --- | --- |
| Diodo | Mesmo mecanismo linear por partes do LED, com parâmetros próprios |
| Potenciômetro | Dois resistores cuja soma é o valor nominal e cujo cursor é o terceiro terminal |
| Chave de alavanca | Mesmo ramo resistivo aberto/fechado da chave atual |
| Fusível/disjuntor | Chave com estado persistente atualizado no tick seguinte a uma violação |

Um dispositivo de proteção não pode abrir no meio da solução corrente. O resultado
do tick atual alimenta sua máquina de estados; uma mudança invalida a rede para o
tick seguinte. Isso evita realimentação temporal escondida dentro do solver DC.

## Solução linear e critérios numéricos

A primeira implementação usará matriz `double` densa e eliminação LU com
pivotamento parcial. A classe de solução DEVE ser `strictfp` para manter semântica
IEEE 754 consistente em Java 8.

Em cada coluna, o pivô é o maior valor absoluto entre as linhas restantes. Em
empate exato, vence a menor linha. Antes da eliminação, cada linha é escalada por
seu maior coeficiente absoluto apenas para o teste de pivô; as equações físicas
não são normalizadas de forma persistente.

Falhas:

- pivô escalado menor ou igual a `1e-12`: `SINGULAR_MATRIX`;
- razão entre maior e menor pivô aceito acima de `1e12`:
  `ILL_CONDITIONED_MATRIX`;
- residual normalizado `||A*x-z||inf / max(1, ||z||inf)` acima de `1e-9`:
  `RESIDUAL_TOO_LARGE`;
- qualquer entrada ou saída `NaN`/infinita: `NON_FINITE_VALUE`.

Quando um pivô falha, a implementação examina a linha reduzida. Coeficientes
todos abaixo da tolerância com lado direito acima dela significam
`CONFLICTING_CONSTRAINTS`; coeficientes e lado direito abaixo dela significam
`SINGULAR_MATRIX` por restrição redundante ou grau de liberdade restante. Assim,
duas fontes ideais sintéticas que exigem tensões diferentes não são confundidas
com duas restrições idênticas.

Resultados dessas falhas não são publicados como medições. A matriz e o vetor
não devem ser incluídos em chat ou pacotes; logs de desenvolvimento podem expor
dimensão, pivô e residual sem dados de jogador.

## Singularidades estruturais

Antes da matriz, um grafo de conectividade condutiva considera resistores,
chaves fechadas, LEDs potencialmente ativos e caminhos internos das fontes.

Nós não alcançáveis de `REFERENCE` são marcados como `FLOATING_NODE`. Como seu
potencial absoluto é indeterminado, suas tensões e correntes não são publicadas.
Uma chave aberta frequentemente cria esse caso; isso não é convertido em zero.

Diagnósticos distintos:

| Código | Significado | Grandezas locais |
| --- | --- | --- |
| `MISSING_REFERENCE` | nenhum GND físico | indisponíveis |
| `FLOATING_NODE` | ilha sem caminho condutivo até GND | somente a ilha é inválida |
| `SINGULAR_MATRIX` | restrições redundantes ou liberdade não detectada | indisponíveis na partição afetada |
| `ILL_CONDITIONED_MATRIX` | escala não confiável | indisponíveis na partição afetada |
| `CONFLICTING_CONSTRAINTS` | equações incompatíveis, confirmado pelo residual | indisponíveis na partição afetada |
| `NON_CONVERGENT` | LEDs não estabilizaram | indisponíveis |

A implementação PODE resolver separadamente a partição ligada à referência e
publicar seus valores enquanto mantém ilhas flutuantes inválidas. Essa partição
deve ser feita antes da montagem; não se remove uma linha singular e continua.

## Curtos, sobrecorrente e potência

Curto não é sinônimo de singularidade. Um caminho de resistência muito baixa
entre a saída da fonte e GND é solucionável por causa dos `10 ohms` internos.

O resultado calcula para cada ramo:

```text
voltage = Va - Vb
current = I(a -> b)
absorbedPower = voltage * current
```

Potência negativa significa que o ramo entrega energia. Diagnósticos são uma
camada posterior à solução:

- LED acima de `20 mA`: `LED_ABOVE_RECOMMENDED_CURRENT`;
- LED acima de `30 mA`: `LED_OVERCURRENT` e avanço do contador atual;
- fonte acima do limite nominal inicial de `100 mA`: `SOURCE_OVERCURRENT`;
- caminho equivalente visto pela fonte abaixo de `1 ohm`, excluindo sua
  resistência interna: `SHORT_CIRCUIT`;
- potência acima do limite declarado pelo componente: `POWER_EXCEEDED`.

Os limiares são diagnósticos e estado de jogo, não clamps numéricos. Um futuro
modelo de fonte com limite de corrente será não linear e exigirá extensão
explícita do conjunto ativo.

A resistência vista por uma fonte é obtida em uma análise auxiliar: remove-se o
ramo interno da fonte alvo, fontes independentes restantes são zeradas preservando
suas resistências internas, LEDs mantêm o estado linear encontrado no ponto de
operação e uma fonte-teste de `1 V` é aplicada entre a saída e `REFERENCE`.
`Rvista = 1 / abs(Iteste)`. Esse valor exclui a resistência interna da fonte alvo
e fundamenta `SHORT_CIRCUIT`; circuito aberto produz resistência infinita como
estado explícito, não um `double` infinito publicado.

`LedState` deve passar a consumir a corrente do `BranchId` do próprio LED. Assim,
dois LEDs na mesma rede podem ter brilho e contador de falha diferentes.

## Contrato de resultados

O substituto de `CircuitResult` será `NodalCircuitResult`, imutável:

```text
NodalCircuitResult
  status: SolveStatus
  topologyFingerprint
  solvedTick
  nodeResults: Map<NodeId, NodeResult>
  branchResults: Map<BranchId, BranchResult>
  terminalToNode: Map<TerminalId, NodeId>
  componentResults: Map<BlockPosition, ComponentResult>
  diagnostics: List<CircuitDiagnostic>
  metrics: SolveMetrics
```

`NodeResult` contém tensão e `ValueValidity`. `BranchResult` contém tensão
orientada, corrente orientada, potência absorvida e validade. `ComponentResult`
agrega seus ramos e oferece o diagnóstico principal para renderização. Os mapas
são imutáveis e iteram em ordem canônica.

`ValueValidity` terá pelo menos `VALID`, `FLOATING`, `INCOMPLETE`, `UNSOLVED` e
`NOT_APPLICABLE`. Valores não `VALID` não carregam um `double` sentinela.

`CircuitDiagnostic` contém:

```text
code
severity: INFO, WARNING, ERROR
positions
nodes
branches
translationKey
numericContext
```

`numericContext` usa chaves conhecidas e números finitos; texto apresentado ao
jogador continua nos arquivos de idioma. Diagnósticos são ordenados por
severidade, código e menor identidade afetada.

### Consultas locais

O gerenciador exporá operações servidor-side:

```text
getNodeResult(TerminalId)
getBranchResult(BranchId)
getComponentResult(BlockPosition)
measureVoltage(TerminalId positive, TerminalId negative)
sharePhysicalNetwork(BlockPosition a, BlockPosition b)
```

`measureVoltage` retorna `Vpositive - Vnegative` somente se ambos os nós forem
válidos e pertencerem à mesma solução completa. O clique em uma face seleciona o
terminal daquela face; clicar no corpo de um fio seleciona seu nó condutor. Uma
seleção ambígua retorna mensagem de terminal, nunca o resultado global.

Resistência entre pontas não é copiada do resultado energizado. Uma análise
auxiliar zera as fontes independentes, preserva suas resistências internas e
aplica uma fonte-teste de `1 V` entre as pontas. Na primeira entrega, presença de
LED ou outro ramo não linear alcançável retorna
`NONLINEAR_MEASUREMENT_UNSUPPORTED`; não se inventa uma resistência equivalente.
Continuidade usa essa mesma análise e um limiar documentado, inicialmente
`10 ohms`. A análise auxiliar respeita os mesmos limites, chunks e diagnósticos
da solução principal e não é armazenada como estado da rede.

Corrente é uma propriedade de ramo. O multímetro não pode deduzir corrente entre
dois fios de um nó equipotencial. Para medir corrente de modo fisicamente correto,
uma etapa posterior deverá inserir um ramo de amperímetro ou selecionar um
componente; até lá o modo de corrente informa a corrente do ramo selecionado e
rejeita junções ambíguas.

`CircuitResult` pode permanecer temporariamente como projeção de compatibilidade
para interfaces antigas, mas não será a representação armazenada no cache 0.3.

## Limites de trabalho

Os limites iniciais são constantes compartilhadas em `NodalLimits`:

| Recurso | Limite | Resultado ao exceder |
| --- | ---: | --- |
| Blocos descobertos | 1.024 | `NETWORK_TOO_LARGE` |
| Terminais materializados | 6.144 | `TERMINAL_LIMIT` |
| Ramos físicos | 2.048 | `BRANCH_LIMIT` |
| Incógnitas MNA | 256 | `MATRIX_LIMIT` |
| LEDs ativos na rede | 64 | `NONLINEAR_LIMIT` |
| Iterações de conjunto ativo | 16 | `NON_CONVERGENT` |

Uma rede de 1.024 fios normalmente colapsa para poucos nós e continua válida. O
limite de 256 incógnitas protege a eliminação densa cúbica; excedê-lo não reduz a
rede nem elimina componentes silenciosamente. Uma RFC futura pode trocar apenas
o backend por matriz esparsa mantendo netlist, stamps e resultados.

Por tick, o gerenciador processa no máximo 4 redes e 512 incógnitas MNA somadas.
O restante fica na fila para o tick seguinte com resultado `PENDING`. O orçamento
é contado por unidades, não por relógio, para que máquinas diferentes processem
a mesma sequência. Uma rede individual dentro de 256 incógnitas sempre cabe.

## Determinismo

- snapshots, terminais, nós, ramos e redes sujas são ordenados pelos comparadores
  canônicos antes de qualquer cálculo;
- `LinkedHashMap`/listas ordenadas são usados no núcleo; `HashMap` nunca define
  ordem observável;
- union-find escolhe o menor representante;
- índices MNA seguem `NodeId`, depois `BranchId` das fontes;
- pivôs empatados escolhem a menor linha;
- LEDs começam `OFF` e são atualizados em ordem canônica;
- diagnósticos e mapas de resultado têm ordem definida;
- o solver não usa aleatoriedade, hora, tick parcial ou paralelismo;
- o cache não altera a inicialização numérica nem o resultado.

O `topologyFingerprint` é SHA-256 de uma codificação binária versionada dos
descritores ordenados, contatos, parâmetros e estados elétricos. Ele serve para
cache e testes, não para segurança nem persistência de mundo.

## Chunks e fronteiras carregadas

`WorldElectricalNetworkFinder` hoje ignora posições para as quais
`world.blockExists` é falso. Na 0.3, a descoberta deve registrar também uma
`ChunkFrontier` quando um terminal aponta para uma posição não carregada.

Regras:

1. nunca chamar APIs que carreguem ou gerem chunks;
2. uma fronteira desconhecida produz `INCOMPLETE_NETWORK`, não `OPEN_CIRCUIT`;
3. nenhum LED avança brilho, sobrecorrente ou queima com resultado incompleto;
4. ao carregar um chunk, invalidar suas posições elétricas e redes incompletas
   que referenciam sua chave;
5. antes de descarregar um chunk, invalidar integralmente toda rede cacheada que
   o intersecta, inclusive entradas `position -> network` em outros chunks;
6. remover resultados publicados para todos os membros dessa rede;
7. não persistir matriz, resultado ou fila suja em NBT.

Cada entrada de cache mantém `Set<ChunkKey> loadedChunks` e
`Set<ChunkKey> frontierChunks`. Isso corrige o comportamento atual de
`unloadChunk`, que remove chaves no chunk mas pode deixar snapshots da mesma rede
associados a posições externas.

## Cache e invalidação

O estado proposto por mundo é:

```text
dirtyRoots: conjunto ordenado de BlockPosition
networkByPosition: Map<BlockPosition, NetworkId>
cacheByNetwork: Map<NetworkId, CachedNetwork>
incompleteByFrontierChunk: Map<ChunkKey, Set<NetworkId>>
generation: long
```

`NetworkId` é a menor posição da rede física mais uma geração interna. Não é
persistido. `CachedNetwork` guarda snapshot, `NodalCircuit`, fingerprint,
resultado, chunks e membros.

`invalidateAround` permanece barato: marca a posição e os seis vizinhos e remove
integralmente cada `CachedNetwork` encontrada por `networkByPosition`. Colocação,
quebra, rotação, mudança de chave, mudança persistente do LED e estado de proteção
usam essa mesma entrada. Não há descoberta recursiva em evento de vizinhança.

No fim do tick:

1. copiar e ordenar raízes sujas;
2. ignorar raiz já coberta por rede processada no mesmo tick;
3. descobrir a rede sem carregar chunks;
4. criar snapshot e fingerprint;
5. reutilizar netlist somente se fingerprint e conjunto de chunks forem iguais;
6. resolver sob orçamento fixo;
7. publicar atomicamente todos os índices e resultados da rede;
8. notificar `ElectricalFeedback` apenas após comparar resultados locais antigos
   e novos.

Uma invalidação que divide uma rede remove o cache antigo inteiro. Cada fragmento
é redescoberto a partir das raízes marcadas e dos antigos membros adjacentes à
mudança. Uma união remove as duas entradas antigas antes de publicar a nova. Não
há janela em que posições apontem para resultados de gerações diferentes.

## Integração com o código atual

### Tipos preservados

- `BlockPosition`: identidade espacial, acrescida de comparador canônico;
- `BoundedNetworkSearch`: BFS limitada, com resultado ampliado para fronteiras;
- `WorldElectricalNetworkFinder`: descoberta Forge, sem responsabilidade de
  montar componentes elétricos;
- `ElectricalNetworkManager`: autoridade por mundo, fila e cache;
- `IElectricalBlock`: compatibilidade inicial da detecção de faces;
- `TileEntityLed`: persistência de queimado e sincronização visual;
- `ElectricalNetworkEvents`: invalidação e tick servidor-side.

### Tipos substituídos ou reduzidos

- `CircuitGraph`: substituído por `NodalCircuit`; não deve ganhar campos de
  terminal como remendo;
- `ElectricalComponent` e `BasicElectricalComponent`: substituídos por snapshots
  e modelos de ramo explícitos;
- `SimpleCircuitSolver`: mantido durante comparação e removido ao fim da migração;
- `CircuitResult`: adaptador temporário; substituído no cache por
  `NodalCircuitResult`;
- `CircuitStatus`: mapeado para `SolveStatus` e diagnósticos estruturados;
- `MultimeterReading.fromNetworkResult`: substituído por consultas entre
  terminais e ramos locais.

### Pacotes propostos

```text
br.com.craftonica.electrical.nodal
  TerminalId, NodeId, BranchId
  ComponentSnapshot, NodalCircuit, NodalCircuitBuilder
  NodalBranch, NodalLimits
  MnaSystem, MnaStamp, DenseLuSolver, DcNodalSolver
  NodalCircuitResult, NodeResult, BranchResult
  CircuitDiagnostic, DiagnosticCode, ValueValidity

br.com.craftonica.network
  WorldComponentSnapshotFactory
  WorldElectricalNetworkFinder
  ElectricalNetworkManager
```

Modelos concretos podem permanecer fábricas de `NodalBranch` em vez de criar uma
classe por bloco. O contrato importante é que a montagem MNA receba dados puros.

## Migração sem quebra de mundo

Não há netlist persistida atualmente. Os blocos, metadados e NBT existentes são a
fonte de migração, portanto nenhum save precisa ser reescrito.

1. Introduzir IDs, snapshots, builder e testes sem ligar ao mundo.
2. Adaptar os blocos atuais para snapshots usando exatamente os metadados já
   gravados: eixo do `BlockTwoTerminal`, face do `BlockSingleTerminal`, ânodo do
   `BlockLed`, bit fechado do `BlockElectricalButton` e `Burned` do
   `TileEntityLed`.
3. Implementar stamps, LU e resultados locais em testes puros Java 8.
4. Executar os dois solvers em modo de comparação apenas em testes e ambiente de
   desenvolvimento. O solver antigo continua publicando resultados nessa etapa.
5. Migrar `TileEntityLed` para corrente do próprio ramo, preservando seu NBT.
6. Migrar multímetro e feedback para consultas locais.
7. Tornar o solver nodal a única fonte de resultados.
8. Remover `CircuitGraph`, `BasicElectricalComponent`, `ElectricalComponent`,
   `SimpleCircuitSolver` e o adaptador global depois que todos os testes e o
   laboratório Prism passarem.

Durante a comparação, o circuito MVP nominal não terá corrente idêntica porque a
fonte 0.3 inclui `10 ohms` e o LED inclui `1 ohm` dinâmico. Com resistor de
`220 ohms`, o valor esperado muda de aproximadamente `13.64 mA` para
`12.99 mA`. A mudança deve aparecer no manual e nas traduções da versão 0.3; não
se deve ocultar a resistência interna para preservar o número antigo.

## Testes analíticos obrigatórios

Todos usam tolerância absoluta de `1e-9` para equações puramente resistivas e
`1e-6` para valores exibidos abaixo. Os testes devem verificar também sinal de
corrente, potência, IDs e ordem dos resultados.

### Casos lineares

| Caso | Montagem | Resultado esperado |
| --- | --- | --- |
| Resistor simples | fonte 5 V/10 ohms, carga 220 ohms | `I = 5/230 = 21.739130 mA`; saída `4.782609 V` |
| Divisor | fonte 5 V/10 ohms, 1 kohm + 1 kohm | corrente `2.487562 mA`; meio `2.487562 V`; saída `4.975124 V` |
| Paralelo | fonte 5 V/10 ohms, 220 ohms em paralelo com 1 kohm | cada ramo obedece sua tensão comum; KCL residual abaixo de `1e-9` |
| Duas fontes | 5 V/10 ohms e 10 V/10 ohms no mesmo nó, sem carga | nó `7.5 V`; uma entrega `250 mA` e a outra absorve `250 mA` |
| Curto | saída da fonte diretamente em GND | `500 mA`, saída `0 V`, `SHORT_CIRCUIT` sem singularidade |
| Chave fechada | carga através de `0.01 ohm` | queda e potência do contato calculadas |
| Chave aberta | carga isolada da referência | `FLOATING_NODE`; nenhum valor sentinela zero |

### LEDs

| Caso | Resultado esperado |
| --- | --- |
| LED direto correto com 220 ohms | `I = (5-2)/(10+220+1) = 12.987013 mA`; LED `2.012987 V` |
| LED invertido | estado `OFF`, corrente ausente/zero físico no ramo e diagnóstico de polaridade |
| Dois LEDs em paralelo com resistores próprios | correntes locais independentes e KCL no nó comum |
| LED sem resistor externo | corrente limitada por `10 + 1 ohms`, diagnóstico de sobrecorrente e curto conforme limiar |
| LED queimado | ramo aberto; estado não muda após reload de NBT |
| Limite de comutação | `Vak = 2.0 V` fica `OFF` deterministicamente |

### Estrutura, falha e limites

- nenhum GND retorna `MISSING_REFERENCE`;
- ilha após chave aberta retorna `FLOATING_NODE` somente nos nós afetados;
- restrições ideais redundantes sintéticas retornam `SINGULAR_MATRIX`;
- duas fontes ideais sintéticas de valores diferentes entre os mesmos nós
  retornam `CONFLICTING_CONSTRAINTS`, sem medições;
- 1.024 fios colapsados em um nó são aceitos;
- 1.025 blocos retornam `NETWORK_TOO_LARGE`;
- 257 incógnitas retornam `MATRIX_LIMIT` antes de alocar matriz excessiva;
- 65 LEDs retornam `NONLINEAR_LIMIT`;
- caso artificial oscilante para no máximo após 16 iterações;
- `NaN`, infinito e resistência não positiva são rejeitados no snapshot.

### Determinismo e integração

- embaralhar inserção de componentes e vizinhos produz o mesmo fingerprint e os
  mesmos bytes lógicos de resultado;
- coordenadas negativas são ordenadas numericamente;
- redes separadas mantêm IDs e resultados independentes;
- alteração de uma chave resolve apenas sua rede;
- quebra que divide rede remove todos os índices antigos;
- união de redes não deixa resultados de gerações diferentes;
- unload de um chunk limpa a rede também nos chunks restantes;
- terminal apontando para chunk descarregado retorna `INCOMPLETE_NETWORK` sem
  carregar o chunk;
- load posterior invalida e resolve a rede completa;
- quatro redes são processadas por tick e o restante permanece `PENDING` na
  ordem canônica;
- cliente dedicado não executa solver e recebe apenas estado visual necessário.

### Regressão do MVP

- orientação atual mapeia corretamente ânodo/cátodo e os dois lados de resistor e
  botão;
- rotação pela `ItemWrench` invalida terminais antigos;
- botão aberto não conduz e botão fechado conduz;
- brilho e queima são baseados na corrente do LED correto;
- `Burned` continua persistido e sincronizado;
- multímetro mede diferença de potencial entre suas duas pontas;
- diagnósticos pt_BR e en_US mantêm fallback e não incluem texto no núcleo.

## Sequência de implementação

Cada etapa deve compilar e manter o solver antigo como padrão até a etapa 7.

1. **Contratos puros:** IDs, comparadores, snapshots, diagnósticos, limites e
   `NodalCircuitResult`.
2. **Builder nodal:** terminais, contatos, union-find canônico, GND, ramos e
   fingerprints, com testes de topologia.
3. **MNA linear:** stamps de resistor/fonte, LU, resíduos, singularidades e casos
   analíticos sem Forge.
4. **Componentes atuais:** fonte Thévenin, chave, LED linear por partes e
   resultados de potência.
5. **Adaptador de mundo:** snapshots reais, fronteiras de chunk e comparação com
   `SimpleCircuitSolver` em circuitos MVP.
6. **Cache 0.3:** gerações atômicas, orçamento fixo, split/merge e unload/load.
7. **Consumidores locais:** `TileEntityLed`, multímetro, renderização e feedback.
8. **Virada:** solver nodal como padrão, atualização do manual e validação Prism
   e servidor dedicado.
9. **Limpeza:** remover solver e grafo série após uma versão sem fallback.
10. **Novos componentes:** fusível/disjuntor, diodo, potenciômetro e chave de
    alavanca sobre os mesmos contratos.

Não se deve combinar as etapas 2 a 7 em um único card. Em particular, cache e
integração Forge não devem entrar no solver matemático.

## Critérios de aceite da arquitetura

- todos os componentes atuais têm mapeamento não ambíguo para terminais e ramos;
- as equações desta RFC reproduzem os testes analíticos;
- paralelo, múltiplas cargas e múltiplas fontes não exigem casos especiais de
  percurso;
- aberto, flutuante, singular, curto e chunk incompleto são estados distintos;
- resultados locais permitem multímetro e LED por componente;
- limites tornam custo de memória, iterações e trabalho por tick explícitos;
- migração preserva metadados e NBT dos mundos 0.2;
- o núcleo permanece Java 8 e independente de Forge;
- nenhuma limitação é substituída silenciosamente por valor inventado.

## Consequências

A MNA é mais complexa que o solver série, mas separa corretamente topologia,
modelo de componente, álgebra e apresentação. A matriz densa limita a primeira
versão a 256 incógnitas, mesmo quando a rede física aceita 1.024 blocos. Esse
limite é deliberado e substituível sem alterar os contratos.

A resistência interna da fonte, a resistência de contato e a resistência dinâmica
do LED mudam ligeiramente os exemplos do MVP. Em troca, curtos passam a ter valor
finito, correntes locais ficam definidas e a simulação deixa de depender de
exceções topológicas. Esses parâmetros precisam ser ensinados como simplificações
do modelo, não escondidos da interface.
