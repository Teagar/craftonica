# RFC 0004: robótica modular física

- Status: proposta implementável
- Marco alvo: núcleo de robótica modular terrestre 2.0
- Card: CRL-70
- Substitui: RFC 0003 para novas montagens
- Dependências: RFC 0001 e RFC 0002
- Última atualização: 2026-09-21

## Resumo

O Craftônica permitirá construir robôs terrestres colocando componentes como
blocos no mundo. A forma, a fiação, a pinagem, a massa, a transmissão, as rodas e
as poses dos sensores virão da montagem do jogador. Não haverá blueprint
obrigatório, quantidade fixa de módulos, pinagem embutida ou entidade pronta que
funcione sem os componentes correspondentes.

Uma ação explícita finaliza uma estrutura rígida conectada e converte seus blocos
em um manifesto canônico dentro de uma entidade persistente. A entidade não move
`Block` ou `TileEntity`: preserva seus descritores, estados e transformações
locais. Desmontar realiza a operação inversa de forma transacional. Eletricidade,
firmware AVR, dinâmica, sensores, colisões e persistência são autoritativos no
servidor.

O primeiro perfil modular cobre corpos rígidos terrestres com rodas
convencionais, incluindo 2WD com rodízio, três rodas, 4WD e skid-steer. Juntas,
servos, braços, esteiras, rodas omni/mecanum, voo e equilíbrio dinâmico exigem
perfis posteriores; não serão aproximados silenciosamente.

Os termos DEVE, NÃO DEVE, DEVERIA e PODE são normativos.

## Correção de direção e relação com a 1.2

A versão 1.2 implementou um robô diferencial com exatamente oito módulos,
pinagem, geometria, sensor frontal e modelo mecânico predeterminados. Ela provou o
runtime móvel, mas não é uma plataforma genérica de construção robótica.

Esta RFC substitui o RFC 0003 para novas montagens. O RFC 0003 permanece como
registro normativo do formato legado `mobile_robot:1`; ele não deve ser estendido
com novos slots ou novos layouts especiais. Código compartilhado pode ser
extraído, mas nenhum requisito legado prevalece sobre este contrato.

## Objetivos

1. Construir robôs somente com componentes públicos colocados no mundo.
2. Derivar comportamento da geometria e das conexões, não de receitas conhecidas.
3. Exigir caminhos estruturais, elétricos e mecânicos reais para produzir efeito.
4. Relacionar energia, corrente, torque, rotação, contato e movimento em SI.
5. Preservar montagens sem perda ou duplicação durante conversão, save e retorno.
6. Manter custo limitado e resultado determinístico no servidor.
7. Expor aproximações educacionais e recusar fenômenos ainda não suportados.

## Não objetivos do primeiro perfil

- simular deformação, fadiga, quebra estrutural ou elementos flexíveis;
- resolver suspensão, pneus deformáveis ou contato contínuo de alta fidelidade;
- reproduzir parâmetros de um motor, bateria ou sensor real sem perfil calibrado;
- suportar juntas ou mais de um corpo rígido na mesma entidade;
- simular aerodinâmica, hidrodinâmica, voo ou equilíbrio bípede;
- aceitar componentes decorativos como substitutos de conexões ausentes;
- fornecer um comando ou GUI que gere um robô pronto.

## Princípios invariantes

### O mundo é a bancada

Componentes são colocados, orientados, ligados e inspecionados como blocos. Uma
interface PODE editar firmware ou mostrar diagnóstico, mas NÃO DEVE definir a
geometria, criar fios virtuais, associar motor a roda ou corrigir uma montagem.

### Nenhum efeito por proximidade implícita

A presença de dois módulos no mesmo manifesto não os conecta. Um efeito exige:

- caminho estrutural válido até o núcleo da montagem;
- contatos face a face compatíveis para fios e terminais elétricos;
- portas mecânicas coincidentes e compatíveis para transmitir esforço;
- alimentação, retorno e sinais válidos para cada componente ativo;
- contato de roda confirmado pela física para gerar tração.

### Dados antes de casos especiais

Catálogo, portas e grafos descrevem os componentes. O runtime NÃO DEVE perguntar
se a montagem é “2WD”, “4WD” ou um blueprint conhecido para decidir a física. Esses
nomes são somente classificações e exemplos documentais.

### Autoridade e tempo

O servidor é a única autoridade. Clientes enviam intenções autenticadas e recebem
pose e estado visual limitado. O tempo simulado avança por ticks confirmados; o
relógio de parede e a taxa de quadros do cliente não alteram resultados.

## Sistema de coordenadas e unidades

O manifesto usa uma origem inteira no bloco estrutural escolhido como âncora e
uma base local ortonormal alinhada aos eixos de blocos no momento da conversão.
Cada módulo armazena posição inteira local, orientação discreta e, quando
necessário, offsets sub-bloco definidos pelo tipo.

- `+Y` local é a face superior da âncora;
- uma rotação da entidade transforma base local em mundo;
- `1 bloco = 1 m`;
- massa em kg, distância em m, tempo em s;
- velocidade linear em m/s e angular em rad/s;
- força em N, torque em N·m, inércia em kg·m²;
- tensão em V, corrente em A, resistência em ohm e potência em W;
- ângulos internos em radianos.

Todo valor persistido ou recebido DEVE ser finito e estar dentro do envelope de
seu tipo. Conversões de unidades ficam nas fronteiras de API; fórmulas internas
NÃO DEVEM misturar blocos por tick com SI.

## Catálogo de componentes

Cada tipo móvel possui identificador estável e versão de esquema. Seu descritor
imutável declara somente capacidades que o bloco realmente oferece:

```text
ComponentType
  id, schemaVersion
  structuralCells[]
  massProperties
  electricalPorts[]
  mechanicalPorts[]
  actuatorProfile?
  contactProfile?
  sensorProfile?
  persistentStateSchema
  visualDescriptor
```

### Estrutura

Uma célula estrutural declara volume de colisão, massa, material e conectores por
face. Conectores só unem células quando posição, face, tipo e orientação são
compatíveis. Fio, sensor ou roda não se torna viga apenas por estar adjacente; o
catálogo declara se e como ele suporta a estrutura.

### Portas elétricas

Uma porta elétrica possui identificador local, posição/face, domínio, direção e
limites. Contatos só existem entre portas compatíveis ou por fio físico. Nomes
como `VCC`, `GND`, `PWM`, `DIR`, `TRIG` e `ECHO` descrevem função no componente,
mas NÃO fixam pinos da RoboBoard. O jogador decide a pinagem pela fiação.

### Portas mecânicas

Uma porta mecânica declara eixo local, gênero ou tipo de acoplamento, graus de
liberdade permitidos e limites. No perfil rígido inicial, uma cadeia válida pode
conectar saída rotativa de motor, acoplador/eixo e cubo de roda. Adjacência sem
portas coincidentes não transmite torque.

### Estado persistente

Estado mutável como firmware, revisão, temperatura, falha, ângulo de eixo e
configuração autorizada é separado do descritor. Campos desconhecidos, ausentes
ou fora dos limites seguem a política de migração; não recebem defaults que
possam energizar um atuador.

## Descoberta da montagem

A ferramenta de finalização parte de uma âncora estrutural escolhida pelo jogador.
O servidor executa uma busca determinística por conectores estruturais, em ordem
lexicográfica de coordenadas e faces.

A descoberta DEVE:

1. visitar somente chunks já carregados;
2. incluir somente tipos allowlisted e versões suportadas;
3. capturar orientação e NBT por adaptador versionado;
4. construir separadamente grafo estrutural, netlist e grafo mecânico;
5. rejeitar coordenadas locais ou identificadores duplicados;
6. validar todos os limites antes de remover qualquer bloco;
7. produzir o mesmo manifesto independentemente da ordem de visita.

Um componente eletricamente ligado, mas sem conexão estrutural, não viaja com o
robô. Um componente estrutural sem ligação elétrica pode viajar, mas permanece
inativo quando exige energia. Cruzar uma fronteira de chunk descarregada retorna
`ASSEMBLY_UNLOADED_BOUNDARY`; a busca nunca carrega o chunk.

O perfil inicial exige exatamente uma RoboBoard ativa por domínio de runtime. A
estrutura pode conter nenhuma RoboBoard e ser diagnosticada como inerte, mas não
pode ser convertida em robô operacional. Várias placas e comunicação entre elas
ficam adiadas e devem retornar diagnóstico explícito.

## Manifesto canônico

O formato inicial é `modular_robot:1`. Ele contém, no mínimo:

```text
manifestId, schemaVersion, catalogRevision
anchor, localBasis
modules[]                 // ordenados por posição e id local
structuralEdges[]         // pares canônicos
electricalNets[]          // terminais ordenados por net id
mechanicalEdges[]         // portas acopladas
mass, centerOfMass, inertiaTensor
compoundCollision
runtimeDescriptor
checksum
```

O `manifestId` é criado uma vez. O checksum SHA-256 cobre a codificação canônica,
sem estado transitório. UUID de entidade, dimensão e pose mundial ficam fora do
checksum estrutural. O manifesto é imutável durante a fase móvel no perfil 1;
alterar a montagem exige desmontar e finalizar novamente.

## Conversão e desmontagem transacionais

A conversão usa journal persistente com estados `PREPARED`, `BLOCKS_CAPTURED` e
`ENTITY_COMMITTED`. Antes de remover blocos, o servidor grava manifesto, NBT
original, posições, permissões e pré-condições. Cada etapa é idempotente.

Na recuperação:

- journal sem entidade e com blocos ausentes restaura os blocos;
- entidade commitada remove apenas resíduos que correspondam ao journal;
- conflito de identidade ou conteúdo entra em `RECOVERY_REQUIRED`;
- recuperação nunca cria drops automáticos.

A desmontagem calcula todas as posições de destino a partir da pose confirmada e
exige chunks carregados, volume livre, suporte permitido e autorização. Grava um
journal inverso antes de colocar qualquer bloco. Se uma posição falhar, nenhuma
alteração parcial permanece e a entidade continua intacta e inerte durante a
tentativa.

Quebrar ou matar a entidade NÃO DEVE despejar componentes. Uma ação administrativa
de recuperação pode exportar um item selado somente se um formato próprio,
limitado e testado for definido posteriormente.

## Rede elétrica móvel

A netlist é extraída dos fios, faces e terminais reais antes da conversão. A
entidade reconstrói nós elétricos isolados usando os mesmos contratos nodais do
mundo fixo. Nenhum net id é inferido pela função do componente.

O solver móvel calcula tensão e corrente necessárias aos periféricos e atuadores.
Uma saída AVR só afeta a porta conectada pelo RoboPort correspondente. Ausência de
VCC, GND, sinal, continuidade ou faixa válida produz entrada flutuante, alta
impedância, componente desligado ou falha conforme o perfil — nunca um valor
ideal inventado.

O perfil de fonte declara tensão nominal, resistência interna e limites de
corrente/potência. Energia armazenada e descarga dinâmica de bateria podem ser
adiadas, mas a fonte NÃO PODE fornecer corrente infinita; enquanto não houver
bateria temporal, o modelo deve ser anunciado como fonte DC regulada educacional.

## Motor CC e ponte H

Cada motor possui parâmetros versionados e finitos:

- resistência de armadura `R`;
- constante de torque `Kt` em N·m/A;
- constante de força contraeletromotriz `Ke` em V·s/rad;
- inércia do rotor `J` e atrito viscoso simplificado `b`;
- corrente, velocidade, potência e temperatura permitidas.

Para cada subpasso aceito:

```text
backEmf = Ke * angularVelocity
current = clamp((terminalVoltage - backEmf) / R, electricalLimits)
motorTorque = Kt * current - b * angularVelocity
```

A ponte H determina tensão terminal a partir de alimentação, direção, PWM,
frenagem e perdas do seu perfil. Coast, brake, avanço, ré e estado inválido são
distintos. Stall aumenta corrente segundo o mesmo circuito; NÃO DEVE ser
representado apenas por velocidade zero. Limites térmicos usam integração
determinística e levam a redução ou desligamento seguro documentado.

Torque só deixa o rotor quando existe caminho mecânico válido até uma roda. Uma
roda encostada ao motor sem eixo/acoplador compatível não recebe esforço.

## Eixos, rodas e contatos

Eixos propagam ângulo, velocidade e torque ao longo de conexões rotativas
compatíveis. O perfil inicial aceita ligação rígida 1:1 e não inventa engrenagens.
Ramificações, loops mecânicos ou relações não suportadas são recusados na
finalização.

Uma roda declara raio, largura, massa, eixo de rotação, material e coeficientes de
contato. Sua posição, eixo e raio vêm do módulo instalado. Rodízio passivo declara
apoio e liberdade de orientação, mas não recebe torque motor.

O contato com o chão é calculado a partir da pose da roda e somente contra chunks
carregados. A força longitudinal é limitada pelo torque no eixo e pelo atrito. O
perfil convencional inicial não gera força lateral comandada; deslizamento
lateral usa atrito simplificado e declarado. Roda sem contato pode girar, mas não
move o corpo.

## Propriedades de massa e corpo rígido

Cada módulo contribui massa `mi`, centro local `ri` e inércia local. O extrator
calcula:

```text
M = sum(mi)
centerOfMass = sum(mi * ri) / M
I = sum(rotate(Ii) + parallelAxis(mi, ri - centerOfMass))
```

Massa total, centro e tensor são armazenados no manifesto e recalculados durante
validação. Divergência indica corrupção. Massa zero, tensor não positivo ou
valores não finitos invalidam a montagem.

O primeiro perfil integra um corpo rígido em terreno Minecraft com gravidade,
apoio, tração de rodas e colisão composta limitada. Não promete suspensão,
capotamento contínuo de alta fidelidade ou conservação de momento entre robôs.
Simplificações devem ser públicas e determinísticas.

## Sensores na pose instalada

Cada sensor possui transformação local derivada de seu bloco e orientação. A pose
mundial usada numa medição é `robotPose * moduleTransform * sensorOffset`. Não há
“frente do robô” global implícita.

O HC-SR04 preserva o contrato acústico de alcance, cone, incidência, área aparente,
material, dispersão e timeout, mas emissão e retorno usam suas portas VCC, GND,
TRIG e ECHO reais. Vários sensores são identificados por módulo, não por slots.

Disparos simultâneos seguem uma ordem canônica. Enquanto interferência acústica
não estiver implementada e validada, o runtime deve serializar disparos
conflitantes ou retornar `ULTRASONIC_CROSSTALK_UNSUPPORTED`; não pode fornecer a
todos a distância perfeita. Sensores nunca carregam chunks.

## Pipeline determinístico por tick

Entidades carregadas são ordenadas por UUID. Para cada tick de 50 ms:

1. capturar ambiente e contatos em chunks carregados;
2. resolver entradas elétricas e eventos de sensores;
3. submeter ou coletar no máximo um quadro AVR confirmado;
4. aplicar a timeline GPIO/PWM à ponte H;
5. resolver rede, corrente, torque e velocidade em subpassos limitados;
6. integrar corpo rígido e colisões;
7. atualizar térmica, diagnósticos e estado persistente;
8. publicar snapshot visual mínimo.

Um resultado AVR atrasado atrasa o tempo virtual do robô. O servidor não pula
quadros, não repete saídas e não integra usando tempo de parede. Dentro de um
robô, módulos, nets, cadeias e contatos são sempre processados por id canônico.

## Limites obrigatórios do perfil 1

Valores podem ser reduzidos após profiling, mas não ampliados sem atualizar esta
RFC, testes de limite e formato de diagnóstico.

| Recurso | Limite |
| --- | ---: |
| blocos visitados na descoberta | 512 |
| extensão por eixo a partir da âncora | 16 blocos |
| módulos no manifesto | 256 |
| células de colisão após fusão | 128 |
| conectores estruturais | 1.024 |
| terminais elétricos | 1.024 |
| redes elétricas | 256 |
| portas e conexões mecânicas | 256 |
| motores | 16 |
| rodas e rodízios | 24 |
| sensores | 32 |
| HC-SR04 ativos por robô | 8 |
| contatos de mundo por subpasso | 128 |
| subpassos mecânicos por tick | 4 |
| ciclos AVR por quadro de 50 ms | 800.000 |
| quadros AVR submetidos/commitados por robô e tick | 1 / 1 |
| bytes do manifesto canônico | 256 KiB |
| bytes de estado móvel persistente | 384 KiB |
| snapshot visual por entidade | 8 KiB |
| robôs modulares processados por dimensão e tick | 64 |
| contatos processados por dimensão e tick | 8.192 |
| bytes de snapshots modulares por jogador e tick | 512 KiB |
| workers AVR globais | 4 |
| fila global de runtime | 64 |

A descoberta possui ainda orçamento de 2 ms por tick e pode continuar por até 20
ticks mantendo snapshot de pré-condições; qualquer mudança nos blocos visitados
aborta. Os limites dimensionais acima são o orçamento global inicial de física e
rede. Ao esgotá-lo, robôs ainda não processados ficam com saídas seguras e tempo
virtual atrasado; o sistema não reduz precisão de modo não determinístico. Uma
configuração pode reduzir esses tetos, mas nunca ampliá-los no perfil 1.

## Estados e diagnósticos

Estados principais:

- `ASSEMBLY`: descoberta/validação sem alteração do mundo;
- `INERT`: entidade válida sem runtime ou esforço ativo;
- `RUNNING`: firmware e solver avançando;
- `FAULT`: falha recuperável de componente ou runtime, atuadores sem esforço;
- `RECOVERY_REQUIRED`: transação incompleta ou conflito de mundo;
- `QUARANTINED`: manifesto inválido, incompatível ou corrompido.

Diagnósticos estruturados incluem módulo, porta/net quando aplicável e mensagem em
pt_BR com fallback en_US. Códigos mínimos:

| Código | Resultado seguro |
| --- | --- |
| `ASSEMBLY_LIMIT_EXCEEDED` | nenhuma conversão |
| `ASSEMBLY_UNLOADED_BOUNDARY` | nenhuma carga de chunk |
| `UNSUPPORTED_COMPONENT` | componente e versão identificados |
| `STRUCTURE_DISCONNECTED` | somente conjunto da âncora é considerado |
| `ELECTRICAL_OPEN` | periférico afetado desligado |
| `ELECTRICAL_OVER_LIMIT` | fonte/ponte/atuador limitado ou desligado |
| `MECHANICAL_PATH_OPEN` | torque não chega ao contato |
| `MECHANICAL_TOPOLOGY_UNSUPPORTED` | nenhuma conversão operacional |
| `WHEEL_NO_CONTACT` | roda livre, sem tração |
| `RUNTIME_BACKPRESSURE` | tempo virtual atrasado, saídas seguras |
| `COLLISION_BUDGET_EXCEEDED` | movimento do tick não é commitado |
| `MANIFEST_CHECKSUM_MISMATCH` | `QUARANTINED` |
| `UNSUPPORTED_SCHEMA` | `QUARANTINED` |
| `RECOVERY_CONFLICT` | `RECOVERY_REQUIRED`, sem drops |

Timeout acústico continua sendo uma leitura válida do HC-SR04, não falha da
plataforma.

## Persistência, migração e compatibilidade

NBT da entidade modular contém versão externa, manifesto canônico, checksum,
identidade, pose, velocidades, estado dos módulos, runtime, contadores de ruído e
journal associado. A leitura valida tamanhos antes de alocar coleções.

### Entidades 1.2

O loader identifica `mobile_robot:1` antes de instanciar comportamento. Há duas
saídas permitidas:

1. migração determinística para `modular_robot:1` somente quando todos os oito
   módulos, estados e transformações podem ser representados sem perda; ou
2. `LEGACY_INERT`, renderizado e desmontável pelo caminho 1.2, sem aceitar novos
   recursos.

A migração é copy-on-write: mantém o NBT original, cria e valida o novo manifesto,
grava checksum e marcador de conclusão, e só então troca o formato ativo. Falha,
crash ou falta de espaço deixa o original como autoridade. Ela ocorre no máximo
uma vez por identidade e nunca cria blocos ou drops.

Versões futuras usam migradores encadeados puros. Esquema mais novo que o servidor,
checksum inválido ou payload acima do limite resulta em `QUARANTINED`. Administrador
pode exportar o NBT bruto e restaurar backup, mas iniciar firmware ou desmontar
automaticamente é proibido.

## Rede e segurança

- mensagens cliente-servidor têm tamanho, frequência, dimensão, distância,
  identidade e permissão validados;
- cliente nunca envia manifesto, massa, netlist, pose de sensor ou resultado de
  física como autoridade;
- snapshots visuais contêm revisão e sequência monotônicas;
- dados completos de firmware/netlist só são enviados ao jogador autorizado que
  abriu a ferramenta correspondente;
- processamento pesado usa estruturas limitadas e snapshots imutáveis;
- worker AVR não recebe referência de mundo nem autoridade para arquivos/rede;
- nenhuma busca, física, colisão ou medição força carregamento de chunk.

## Três montagens de referência, sem blueprints

Essas montagens são fixtures e documentação. Seus nomes ou geometrias NÃO DEVEM
aparecer em desvios de lógica do runtime de produção.

### A — 2WD estreito com rodízio

Estrutura longitudinal, duas rodas motrizes laterais, motores independentes,
rodízio traseiro e HC-SR04 frontal. A distância entre rodas e o ponto do sensor
vêm das transformações locais. Trocar o sensor para uma face lateral muda apenas
sua pose; não exige configuração “sensor lateral”.

### B — plataforma larga 4WD

Estrutura retangular com quatro cadeias motor-eixo-roda e maior massa. Pode usar
quatro canais de ponte H compatíveis ou duas pontes. Correntes, torques e contatos
são somados a partir dos grafos. Não existe flag `fourWheelDrive`.

### C — triciclo assimétrico

Duas rodas motrizes em posições não espelhadas e um rodízio passivo formam centro
de massa e braços de força assimétricos. O
resultado pode ser pouco eficiente ou instável, mas deve ser finito e derivado da
montagem, não corrigido para parecer um chassi diferencial.

As três usam os mesmos tipos, extrator, solver e integrador. Alterar posição,
orientação, raio, massa, fio ou acoplamento deve mudar o manifesto e o efeito
correspondente.

## Gates de implementação

### Contratos puros

- catálogo rejeita massa negativa, NaN, ids/portas duplicados e versão ausente;
- canonicalização independe da ordem de descoberta;
- massa, centro e tensor conferem com fixtures analíticas;
- netlist não cria conexão por nome ou proximidade;
- grafo mecânico só transmite esforço por portas compatíveis;
- todas as coleções e payloads falham no limite exato.

### Conversão e persistência

- falha injetada em cada etapa restaura exatamente mundo ou entidade;
- save/reload preserva UUID, manifesto, transformações e runtime;
- desmontagem obstruída não altera entidade nem mundo;
- corrupção, versão futura e checksum inválido entram em quarentena;
- fixtures 1.2 migram uma vez ou permanecem `LEGACY_INERT` sem perda.

### Física e I/O

- desconectar VCC, GND, PWM, direção, eixo ou roda elimina o efeito correto;
- curvas no-load, carga e stall do motor seguem tolerâncias publicadas;
- mover massa, roda ou sensor muda centro, contato ou medição;
- roda suspensa gira sem tração;
- repetição do mesmo trace produz hashes elétrico, AVR e mecânico iguais;
- colisão, sensor e descoberta não carregam chunks.

### Forge e multiplayer

- A, B e C são construídas com blocos públicos em mundo novo;
- nenhum comando cria entidade pronta; a ferramenta somente finaliza/desmonta;
- dois clientes observam a mesma pose e estado calculados pelo servidor;
- unload/reload e reconexão não avançam tempo nem duplicam identidade;
- soak no envelope máximo respeita CPU, memória, rede, filas e TPS publicados.

## Sequência de implementação

1. contratos e catálogo independentes do Forge;
2. descoberta estrutural e manifesto canônico;
3. journals genéricos de conversão/desmontagem;
4. netlist elétrica móvel sem pinagem fixa;
5. motor CC, ponte H e grafo mecânico;
6. rodas, contatos e corpo rígido derivado;
7. acoplamento eletromecânico e budgets;
8. sensores pela transformação local;
9. migração/persistência e protocolo visual;
10. fixtures, multiplayer, soak e documentação educacional.

Cada etapa mantém a versão 1.2 utilizável ou explicitamente legado-inerte. Nenhuma
etapa deve anunciar “robótica modular” antes de as três montagens de referência
passarem pelos mesmos caminhos de produção.

## Questões adiadas

O RFC 0005 define o perfil inicial para transmissões, juntas, servos e corpos
articulados. Os demais itens abaixo continuam adiados quando não cobertos por um
perfil posterior explícito.

- múltiplas RoboBoards e barramentos de comunicação;
- bateria com estado de carga e envelhecimento;
- correias flexíveis, diferenciais e transmissões fora do perfil RFC 0005;
- juntas multi-GDL e loops articulados fora do perfil RFC 0005;
- contato de esteiras, rodas omni e mecanum até seus perfis derivados do RFC 0005;
- suspensão e pneus deformáveis;
- dano, quebra estrutural e reparo em movimento;
- interferência acústica completa entre vários sonares;
- voo, propulsão aquática e equilíbrio dinâmico.

Recursos adiados devem ser diagnosticados ou permanecer inertes. Não podem ser
imitados por comandos especiais, movimento cinemático oculto ou componentes
virtuais.
