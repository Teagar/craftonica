# RFC 0005: grafo cinemático modular

- Status: proposta implementável
- Marco alvo: mecanismos articulados e mobilidade avançada 2.x
- Card: CRL-83
- Estende: RFC 0004
- Dependências: RFC 0002 e RFC 0004
- Última atualização: 2026-09-21

## Resumo

Esta RFC estende o robô modular rígido com corpos, juntas e transmissões físicas.
Blocos ligados rigidamente formam corpos; conectores de junta compatíveis separam
corpos e concedem exatamente um grau de liberdade rotativo ou linear. Motores e
servos aplicam esforço por caminhos mecânicos explícitos. Relação de transmissão,
folga, rendimento, limites de curso, corrente, temperatura, colisão própria e
estado persistido são derivados da montagem e limitados pelo catálogo.

O servidor resolve o mecanismo em passos determinísticos. O cliente recebe somente
poses e diagnósticos visuais. Nenhum servo teleporta uma peça, nenhuma engrenagem
cria energia e nenhuma proximidade substitui eixo, mancal, dente, junta ou fio.

Os termos DEVE, NÃO DEVE, DEVERIA e PODE são normativos.

## Relação com o núcleo terrestre

O RFC 0004 continua normativo para catálogo, descoberta, manifesto, eletricidade,
AVR, conversão transacional, segurança de chunks e corpo raiz terrestre. Esta RFC
substitui somente as restrições “um corpo rígido” e “transmissão 1:1” para montagens
que declarem o perfil `articulated_mechanism:1`.

Montagens `modular_robot:1` existentes não são reinterpretadas. Um migrador só
eleva o perfil quando todas as arestas mecânicas têm significado idêntico; caso
contrário, o manifesto permanece no perfil rígido anterior.

## Objetivos

1. Representar braços, garras e atuadores lineares com o mesmo grafo.
2. Derivar corpos rígidos de conexões estruturais, sem blueprint ou slot fixo.
3. Propagar velocidade, torque e potência por transmissões físicas.
4. Impor limites de posição, velocidade, esforço, corrente e temperatura.
5. Resolver contato externo e colisão própria sem carregar chunks.
6. Persistir todo estado dinâmico sem impulso duplicado após reload.
7. Recusar topologias que o solver inicial não possa resolver com segurança.

## Não objetivos do perfil 1

- juntas esféricas, universais ou com mais de um grau de liberdade;
- elos flexíveis, cabos, correias elásticas ou deformação estrutural;
- backlash dinâmico de alta fidelidade, vibração de dentes ou fadiga;
- loops cinemáticos fechados, mecanismos paralelos ou diferenciais;
- controle inverso, planejamento de trajetória ou estabilização automática;
- contato contínuo exato ou conservação perfeita de momento em impactos;
- aerodinâmica, voo, hidrodinâmica, bípedes ou elementos finitos.

Recursos fora do perfil são recusados, não aproximados silenciosamente.

## Unidades e referenciais

Todas as grandezas seguem SI. Posição linear usa metros, ângulo radianos,
velocidade m/s ou rad/s, força N, torque N·m, massa kg, inércia kg·m² e potência W.

Cada corpo `Bi` possui:

```text
Body
  bodyId
  modules[]
  parentJoint?
  mass, centerOfMass, inertiaTensor
  compoundCollision[]
  poseRelativeToParent
  linearVelocity, angularVelocity
```

O corpo raiz usa a pose mundial da entidade. Corpos filhos armazenam coordenada e
velocidade generalizadas da junta; sua pose é calculada por composição de
transformações, nunca persistida como segunda autoridade independente.

Transformações usam base ortonormal destra. Quaternions ou matrizes internas são
normalizados em fronteiras definidas. O perfil 1 limita cada junta a um eixo local
cardinal do bloco, mas o resultado mundial pode assumir orientação arbitrária pela
composição dos ancestrais.

## Extração dos corpos

O grafo estrutural do RFC 0004 é particionado removendo conectores classificados
como junta. Cada componente conexo restante forma um corpo rígido.

A extração DEVE:

1. ordenar módulos e arestas canonicamente;
2. exigir exatamente um corpo raiz ligado à âncora;
3. atribuir cada módulo a exatamente um corpo;
4. recalcular massa, centro, inércia e colisão por corpo;
5. rejeitar corpo sem massa positiva ou tensor finito positivo;
6. rejeitar conexão estrutural rígida que contorne uma junta;
7. rejeitar módulo compartilhado entre corpos;
8. nunca consultar chunks fora da montagem já capturada.

Uma junta é uma aresta entre dois corpos distintos. Autoconexão é inválida. Duas
juntas entre o mesmo par criam loop e são recusadas no perfil inicial.

## Grafo cinemático

O perfil 1 aceita uma árvore enraizada dirigida. Cada corpo não raiz possui
exatamente uma junta pai. A orientação pai–filho é canônica pelo caminho a partir
da âncora, não pela ordem de descoberta.

```text
KinematicGraph
  rootBodyId
  bodies[]
  joints[]
  transmissionGraph
  actuators[]
  sensors[]
  collisionExclusions[]
```

São inválidos:

- ciclo entre corpos;
- corpo órfão ou com dois pais;
- aresta de junta entre portas incompatíveis;
- eixo degenerado ou referenciais não ortogonais;
- junta que faria volumes ligados iniciarem em penetração além da tolerância;
- coordenada inicial fora dos limites;
- mais de um atuador rígido impondo comandos incompatíveis ao mesmo GDL.

Diagnóstico: `KINEMATIC_TOPOLOGY_UNSUPPORTED`, sem conversão operacional.

## Juntas de um grau de liberdade

### Junta rotativa

Uma junta `REVOLUTE` permite rotação `q` em torno de um eixo fixo no corpo pai.
Declara pivô em ambos os corpos, eixo, intervalo `[qMin,qMax]`, velocidade máxima,
torque máximo, atrito viscoso, atrito estático simplificado e restituição no limite.

```text
childPose = parentPose * parentFrame * rotate(axis, q) * inverse(childFrame)
```

`qMin < qMax`, ambos finitos e com amplitude máxima de `2π` no perfil 1. Uma junta
de giro contínuo usa `CONTINUOUS_REVOLUTE`, sem batente, mas ainda limita velocidade
e torque; sua posição persistida é normalizada e mantém contador inteiro de voltas
quando necessário para encoder.

### Junta linear

Uma junta `PRISMATIC` permite translação `q` sobre um eixo fixo.

```text
childPose = parentPose * parentFrame * translate(axis * q) * inverse(childFrame)
```

O curso máximo inicial é `4 m`. Limites, velocidade, força, atrito e restituição
são explícitos. Um bloco apenas encostado em um trilho não constitui carro linear.

### Batentes

Batentes são restrições unilaterais. O solver impede penetração crescente e aplica
impulso limitado. Restituição padrão é zero. Um atuador que continua comandando
contra o limite pode manter corrente e aquecer; posição não atravessa o batente e
o esforço não desaparece.

Tolerâncias iniciais:

- posição angular: `1e-5 rad`;
- posição linear: `1e-5 m`;
- velocidade de repouso: `1e-4 rad/s` ou `1e-4 m/s`;
- correção máxima de erro por subpasso: 20% do erro, limitada pelo perfil.

## Portas e componentes mecânicos

O catálogo adiciona descritores versionados:

```text
JointPort
  id, kind, localFrame, role(parent|child|either), limits

TransmissionPort
  id, rotational|linear, localAxis, direction(input|output|passive)
  maximumTorqueOrForce, maximumSpeed

TransmissionProfile
  kind, ratio, efficiency, backlash, reflectedInertia, limits
```

Mancais suportam um eixo e definem onde ele pode atravessar um corpo, mas não geram
torque. Eixos conectam portas coaxiais. Engrenagens só engrenam quando seus volumes
de passo, módulos e planos são compatíveis e seus dentes estão em contato segundo
as portas declaradas. Adjacência genérica não transmite potência.

## Grafo de transmissão

Transmissões são separadas do grafo de corpos. Seus nós são coordenadas mecânicas
de eixo ou junta; arestas transformam esforço e velocidade.

Para uma aresta ideal orientada entrada → saída:

```text
omegaOut = omegaIn / ratio
torqueOut = torqueIn * ratio * efficiency
powerLoss = abs(torqueIn * omegaIn) - abs(torqueOut * omegaOut)
```

`ratio` é positivo e finito no intervalo `[1/256,256]`. O sinal de rotação vem da
orientação/engrenamento, não do sinal da razão. `efficiency` pertence a `(0,1]`.
Perda vira calor no componente; não some nem retorna como potência útil.

O perfil 1 aceita árvores de transmissão sem ramificação de potência. Uma cadeia
pode conter eixos, mancais, acopladores e até oito estágios de engrenagem. Produto
de relações e limites é calculado de modo canônico. São recusados:

- loop de transmissão;
- saída alimentada por duas fontes rígidas;
- uma entrada dividida para múltiplas saídas;
- engrenagens incompatíveis ou razão total fora dos limites;
- ligação rotativa–linear sem componente conversor explícito;
- razão zero, NaN, infinita ou rendimento não físico.

Diagnóstico: `TRANSMISSION_TOPOLOGY_UNSUPPORTED`; nenhum esforço atravessa o ramo.

## Atuadores

Todo atuador liga um domínio elétrico a uma coordenada mecânica. O caminho exige
alimentação, retorno, sinal e acoplamento reais.

### Motor CC

O modelo do RFC 0004 permanece. A velocidade usada na back-EMF é a do rotor
refletida pela transmissão. Inércia e carga da saída retornam ao rotor pela relação
ao quadrado. Stall, limite e colisão produzem carga, corrente e aquecimento.

### Servo educacional

O servo possui motor CC, redução, sensor de posição e controlador fechado como um
componente físico. PWM define referência, não posição instantânea.

```text
target = mapPulseWidthToRange(pulse)
error = target - measuredPosition
requestedTorque = clamp(Kp*error - Kd*velocity, torqueLimit)
motorVoltage = clamp(controller(requestedTorque), supply)
```

O perfil declara faixa aceita (tipicamente 1–2 ms), frequência, deadband, `Kp`,
`Kd`, velocidade, torque, corrente e térmica. Sinal ausente aplica política
declarada `HOLD`, `COAST` ou `SAFE_POSITION`; nunca mantém comando inventado.
Sem VCC/GND, torque é zero. Sobrecarga mantém erro e corrente limitada, podendo
acionar proteção térmica. O servo NÃO escreve `q = target`.

### Atuador linear

Um motor e conversor explícito (fuso ou pinhão-cremalheira) transformam torque em
força. Passo/raio e rendimento determinam relação; curso vem da junta prismática.
Fim de curso físico é sensor separado ou batente mecânico, não ambos implicitamente.

## Acoplamento eletromecânico

Cada subpasso executa, em ordem canônica:

1. amostrar contatos, fins de curso e posições atuais;
2. aplicar um quadro AVR confirmado;
3. resolver alimentação e controladores de atuador;
4. transformar velocidades de saída em velocidade de rotor;
5. calcular back-EMF, corrente, torque e perdas;
6. propagar esforço pelas transmissões;
7. resolver juntas, batentes, contato e colisão própria;
8. integrar velocidades e coordenadas;
9. atualizar térmica, energia e diagnósticos;
10. publicar pose visual somente após commit atômico.

Se qualquer orçamento acabar, o subpasso inteiro não é commitado. Tempo virtual e
sequências permanecem parados; não há estado elétrico novo com mecânica antiga.

## Dinâmica e solver

O perfil inicial usa dinâmica de corpos articulados educacional com restrições por
impulsos sequenciais determinísticos. Não é animação cinemática. Gravidade, inércia,
forças externas, torque de atuador, contato e reação de junta participam do passo.

Integração: semi-implícita, `strictfp`, dois subpassos de 25 ms por tick normal e
até quatro quando já permitido pelo orçamento do RFC 0004. A ordem das restrições é
`jointId`, tipo e índice de contato. Iterações usam contagem fixa, nunca convergência
dependente da máquina.

Primeiro perfil:

- 8 iterações de velocidade por subpasso;
- 4 iterações de posição por subpasso;
- Baumgarte limitado para deriva;
- warm-start somente com impulsos persistidos do passo anterior e checksum;
- sem sleeping de corpos individuais; mecanismo inteiro pode ficar inerte;
- falha numérica aborta o passo e zera esforço dos atuadores.

O solver deve produzir valores finitos. Exceder velocidade, impulso ou correção
admissível gera `MECHANISM_NUMERIC_FAULT`, estado anterior preservado e atuadores
desenergizados até confirmação de recuperação.

## Singularidades e redundância

Uma árvore de juntas de 1 GDL em dinâmica direta não exige inversão de Jacobiano
global para movimento livre. Singularidade geométrica ainda pode aparecer em
conversores, controles derivados ou contato redundante.

O perfil 1 NÃO implementa cinemática inversa. Comandos são posição/velocidade/esforço
por junta ou atuador identificado pela fiação. Portanto, braço esticado não recebe
velocidade cartesiana “corrigida”.

Restrições redundantes de contato são resolvidas na ordem canônica com impulsos
limitados. Se o erro residual exceder tolerância por quatro subpassos consecutivos,
o mecanismo entra em `CONSTRAINT_DIVERGENCE`, congela coordenadas no último estado
válido e remove esforço ativo. Não são adicionadas molas ocultas.

## Colisão própria e mundo

Cada corpo conserva colisão composta do RFC 0004. Pares são derivados em ordem
canônica. Corpos diretamente ligados podem excluir somente os volumes declarados
pela própria junta; não há exclusão automática de corpos adjacentes inteiros.

O broad phase usa AABBs por corpo. Narrow phase inicial usa volumes convexos/AABBs
conservadores já suportados. Colisão própria gera contato bilateral entre corpos;
ação e reação entram no mesmo solve. Pares além do orçamento não são ignorados: o
subpasso não é commitado e retorna `SELF_COLLISION_BUDGET_EXCEEDED`.

Consultas ao mundo terminam antes do primeiro chunk descarregado. Se qualquer
volume varrido necessário cruza região não carregada, o mecanismo para em seu último
estado seguro com `UNLOADED_COLLISION_BOUNDARY`; nenhum chunk é solicitado.

## Perfis de mobilidade avançada

Este grafo fornece eixos, velocidades e esforços às extensões de contato, mas não
define por si só uma esteira ou roda omnidirecional.

- esteira exige caminho físico fechado de rodas dentadas e segmentos, tensão
  válida e perfil de contato agregado limitado; uma textura de esteira não cria
  tração;
- roda omni/mecanum declara roletes, direção de força permitida e perdas; a força
  lateral deriva da orientação instalada e nunca de uma flag de chassi;
- falha ou ausência de segmento, rolete, eixo ou contato elimina apenas o esforço
  correspondente e produz diagnóstico;
- esses contatos usam o mesmo corpo/junta/transmissão, budgets e autoridade desta
  RFC, mas exigem perfis normativos próprios nos CRL-88 e CRL-89.

## Modelos de referência

São validações documentais, não blueprints reconhecidos pelo runtime.

### Braço serial

Corpo raiz → junta revoluta de ombro → elo 1 → junta revoluta de cotovelo → elo 2.
Dois servos alimentados e sinalizados separadamente acoplam às duas juntas. O mesmo
grafo aceita elos com massa, comprimento e limites distintos. Braço esticado é
válido em dinâmica direta; controle cartesiano inverso permanece não suportado.

### Garra paralela simplificada

O perfil inicial representa duas garras independentes: corpo palma com duas juntas
prismáticas filhas, cada uma com seu atuador linear. Um único motor dividindo potência
por engrenagens para ambas é ramificação e deve ser recusado até perfil posterior.
Limites de curso fecham a garra e contato próprio/externo produz carga real.

### Mecanismo linear

Corpo raiz → junta prismática → carro. Motor CC → redução → fuso explícito → junta.
O passo do fuso converte torque em força. Remover o mancal abre a transmissão;
remover VCC desenergiza o motor; atingir o curso mantém posição e aumenta carga.

Os três exemplos usam os mesmos `Body`, `Joint`, `Transmission` e `Actuator`.

## Ciclos e casos recusados

| Caso | Diagnóstico | Estado seguro |
|---|---|---|
| quatro barras ou paralelogramo fechado | `KINEMATIC_LOOP_UNSUPPORTED` | nenhuma ativação |
| duas juntas pai para o mesmo corpo | `MULTIPLE_JOINT_PARENTS` | nenhuma ativação |
| loop de engrenagens | `TRANSMISSION_LOOP_UNSUPPORTED` | ramo sem esforço |
| relações incompatíveis no mesmo eixo | `TRANSMISSION_CONSTRAINT_CONFLICT` | ramo sem esforço |
| motor rígido duplo no mesmo GDL | `ACTUATOR_CONFLICT` | atuadores desligados |
| junta inicial fora do curso | `JOINT_LIMIT_INVALID` | quarentena/recaptura |
| penetração inicial excessiva | `INITIAL_SELF_COLLISION` | nenhuma ativação |
| solver não finito/divergente | `MECHANISM_NUMERIC_FAULT` | rollback do subpasso |
| chunk necessário descarregado | `UNLOADED_COLLISION_BOUNDARY` | pose anterior |

## Persistência

O manifesto cinemático é imutável e checksummed. Inclui partição de corpos, frames
de junta, limites, transmissão, exclusões de colisão e versões de perfil.

O estado dinâmico versionado inclui:

- coordenada e velocidade de cada junta;
- contador de voltas para juntas contínuas;
- velocidade de rotores e eixos;
- corrente, temperatura, shutdown e controlador de atuadores;
- impulsos limitados de warm-start;
- sequência de quadro AVR e tick virtual;
- pose/velocidade do corpo raiz;
- contadores determinísticos de sensores e falhas.

O envelope SHA-256 do CRL-80 é ampliado por migrador. Save não avança solver. Unload
cancela jobs e descarta resultados não commitados. Reload não reaplica impulso,
quadro ou energia. Campo ausente, tamanho incorreto, NaN, schema futuro ou checksum
inválido resulta em `QUARANTINED`, preservando o payload bruto e sem drops.

## Snapshot visual e multiplayer

O snapshot de spawn estende o CRL-81 com `bodyId` por módulo, frames de junta e
revisão cinemática. Atualizações enviam pose raiz e coordenadas quantizadas/limitadas
das juntas. Cliente compõe transformações e interpola; nunca resolve contato,
limites, servo ou transmissão.

Orçamento inicial adicional:

- até 32 coordenadas visuais por entidade;
- posição angular quantizada em 16 bits dentro do curso;
- posição linear quantizada em 16 bits dentro do curso;
- sequência monotônica de 32 bits;
- até 256 bytes por atualização de mecanismo;
- atualização normal a cada 2 ticks, reduzível por distância;
- manifesto, netlist e firmware continuam fora das atualizações.

Pacote inválido no cliente é descartado. Cliente nunca envia coordenada de junta
como autoridade.

## Limites obrigatórios do perfil 1

| Recurso | Limite |
|---|---:|
| corpos rígidos por mecanismo | 32 |
| juntas por mecanismo | 31 |
| profundidade da árvore | 12 |
| coordenadas de junta | 31 |
| arestas de transmissão | 64 |
| estágios por caminho | 8 |
| atuadores | 16 |
| servos | 16 |
| pares potenciais de colisão própria | 256 |
| contatos próprios ativos por subpasso | 64 |
| contatos totais mecanismo–mundo por subpasso | 128 |
| restrições totais por subpasso | 256 |
| iterações de velocidade / posição | 8 / 4 |
| subpassos por tick | 4 |
| passos de drive/transmissão por tick | 128 |
| bytes extras de manifesto cinemático | 128 KiB |
| bytes extras de estado dinâmico | 128 KiB |
| bytes de atualização visual | 256 |
| mecanismos articulados por dimensão/tick | 32 |
| restrições articuladas por dimensão/tick | 4.096 |

Esses limites coexistem com os tetos do RFC 0004; vale sempre o menor orçamento
remanescente. Saturação atrasa mecanismos por janela circular de UUID. Não reduz
iterações, não omite colisões e não expande filas.

## Segurança e autoridade

- descoberta, validação, solver e persistência executam no servidor;
- nenhuma etapa carrega chunk;
- cliente envia somente intenção autenticada de ferramenta/firmware;
- PWM e setpoints vêm de commits AVR confirmados e RoboPorts físicos;
- limites de catálogo não são sobrescritos por NBT do cliente;
- falha elétrica desenergiza esforço, mas não teleporta o mecanismo;
- toda mutação de mundo continua transacional e sem drops automáticos;
- workers não recebem `World`, filesystem, rede ou autoridade de entidade.

## Gates de implementação

### Contratos puros

- partição de corpos é canônica e independente da ordem de descoberta;
- braço, garra independente e mecanismo linear usam o mesmo grafo;
- ciclos, múltiplos pais e frames inválidos são recusados;
- composição de transformações confere com fixtures analíticas;
- relação conserva potência até perdas declaradas;
- inércia/carga refletida usa o quadrado da relação;
- cada limite falha exatamente no teto publicado.

### Física

- servo converge por torque sem salto de posição;
- stall e batente elevam corrente/temperatura dentro dos limites;
- cortar energia remove torque e mantém dinâmica passiva;
- gravidade derruba elo sem sustentação;
- colisão própria impede atravessamento e aplica reação bilateral;
- mesmo trace produz bits idênticos após save/reload;
- orçamento esgotado não produz commit parcial.

### Forge e multiplayer

- mecanismos são construídos com blocos públicos no mundo;
- desmontagem articulada é transacional e preserva poses/estados necessários;
- dois clientes observam as mesmas juntas sem física local;
- unloaded boundary não chama geração/carregamento de chunk;
- soak no envelope máximo respeita TPS, memória, rede e filas publicados.

## Sequência de implementação

1. contratos puros do grafo, frames, limites e canonicalização;
2. eixos, mancais, engrenagens e redução;
3. servo elétrico e controle AVR;
4. juntas rotativas/lineares e batentes;
5. solver articulado, contato e colisão própria;
6. esteiras modulares;
7. rodas omni/mecanum;
8. encoders, fins de curso e IMU;
9. fixtures de braço, garra e bases avançadas, soak e multiplayer.

Cada etapa deve permanecer inerte quando a seguinte for necessária. Renderização
não pode anteceder autoridade física e nunca será usada para esconder ausência de
solver.

## Questões adiadas

- loops cinemáticos e mecanismos paralelos;
- diferenciais, divisão/soma de potência e planetárias;
- juntas multi-GDL e suspensão completa;
- controle cartesiano, cinemática inversa e planejamento;
- backlash dinâmico, elasticidade e vibração;
- quebra, fadiga e dano estrutural;
- cabos, correias flexíveis e correntes detalhadas;
- voo, água e equilíbrio dinâmico.

Qualquer implementação futura dessas questões exige novo perfil/versionamento e
gates próprios; não pode alterar silenciosamente mecanismos persistidos.
