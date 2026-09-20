# RFC 0003: plataforma robótica móvel

- Status: proposta implementável
- Marco alvo: robô diferencial autônomo
- Card: CRL-57
- Dependências: RFC 0001, RFC 0002, CRL-55 e CRL-56
- Última atualização: 2026-09-20

## Resumo

O primeiro robô móvel do Craftônica será montado como uma estrutura limitada de
blocos e convertido, por uma ação explícita do jogador, em uma entidade persistente
controlada pelo servidor. A entidade não moverá `Block`, `TileEntity` ou chunks:
ela carregará descritores imutáveis dos módulos, um `RoboBoardState`, a configuração
elétrica interna e o estado mecânico necessário para simular um chassi diferencial.

O perfil inicial contém exatamente uma RoboBoard, um HC-SR04 frontal, uma ponte H,
dois motores com rodas, uma alimentação de 5 V e um GND. O firmware Arduino real
mede TRIG/ECHO e controla direção/PWM. Sensores, firmware, atuadores, colisão,
persistência e autorização continuam servidor-autoritativos.

A simulação avança em quadros lógicos de 50 ms. Cada quadro captura o ambiente,
executa 800.000 ciclos AVR, aplica a linha temporal de GPIO/PWM e integra a física.
Se o worker atrasar, o tempo simulado do robô atrasa; o servidor nunca pula quadros,
repete física ou deriva o resultado do relógio de parede.

Os termos DEVE, NÃO DEVE, DEVERIA e PODE são normativos.

## Motivação

O hardware atual é fixo no mundo:

- `TileEntityRoboBoard` agenda o runtime a partir de uma posição de bloco;
- `RoboBoardIoBridge` encontra `TileEntityRoboPort` carregadas;
- `UltrasonicRaycaster` parte de coordenadas inteiras e orientação cardinal;
- `BlockEducationalActuator` modela o motor somente como carga resistiva;
- identidade de request AVR contém dimensão e coordenadas da placa.

Mover essas TileEntities a cada tick quebraria índices de rede, invalidação nodal,
NBT de chunk, identidade de requests em voo e sincronização cliente-servidor. Uma
coleção de blocos também não fornece rotação contínua, colisão de veículo nem uma
transação segura de montagem.

A fronteira correta é separar:

1. **montagem física**, editável e composta por blocos;
2. **manifesto do robô**, puro, validado e independente de Forge;
3. **host móvel**, responsável por runtime, I/O e física;
4. **entidade Forge**, responsável por vida no mundo e replicação visual.

## Objetivos

- Preservar a construção física no mundo, sem bancada 2D de montagem.
- Executar sketches do perfil Arduino já publicado, sem DSL específica de robô.
- Permitir avanço, ré, curvas e giro no próprio eixo por tração diferencial.
- Medir obstáculos a 2–400 cm com limitações acústicas, não distância perfeita.
- Persistir firmware, sketch, checkpoint, montagem e pose entre recargas.
- Manter resultados determinísticos sob a mesma sequência de estado do mundo.
- Conter custo por robô e nunca carregar chunks para física ou sensoriamento.
- Recusar montagens e estados desconhecidos sem perder ou duplicar componentes.

## Fora de escopo do primeiro perfil

- voo, barcos, esteiras, suspensão física e dinâmica de corpo rígido genérica;
- braços, garras, juntas, inventário transportador e interação com blocos;
- terreno com degraus, escadas, lajes, líquidos ou inclinação contínua;
- empurrar jogadores, criaturas, itens ou outros robôs;
- mais de uma RoboBoard ou mais de um HC-SR04 por robô;
- SLAM, mapa global, câmera, GPS, rádio ou coordenação entre robôs;
- bateria eletroquímica, temperatura de motor e desgaste mecânico;
- simulação CFD, propagação da onda de 40 kHz ou interferência entre sonares;
- montagem atravessando fronteira de chunk;
- execução de física ou firmware no cliente.

Essas funções exigem RFCs próprias. Não serão aproximadas silenciosamente.

## Decisões normativas

1. Blocos da montagem NÃO DEVEM ser movidos pelo robô.
2. A ativação DEVE produzir um manifesto canônico antes de alterar o mundo.
3. A entidade DEVE guardar estado próprio; posições absolutas de blocos não são
   identidade persistente dos módulos móveis.
4. Runtime AVR, sensores, atuadores e física DEVEM avançar somente no servidor.
5. Um quadro lógico DEVE representar 50 ms e 800.000 ciclos AVR.
6. Quadros NÃO DEVEM ser pulados para alcançar o tempo do mundo.
7. O robô NÃO DEVE mover-se enquanto o resultado AVR de seu quadro está pendente.
8. Ordem de `HashMap`, entidades e chunks NÃO DEVE alterar o resultado.
9. Sensores e colisão NÃO DEVEM carregar chunks.
10. Todo valor físico deve ser finito, limitado e expresso nas unidades deste RFC.
11. Firmware em falha, alimentação inválida ou montagem inconsistente DEVE zerar
    os esforços dos motores antes do próximo passo mecânico.
12. O cliente recebe somente pose e estado visual sanitizado; nunca decide
    sensores, GPIO, colisão, autorização ou resultado de montagem.
13. Ativação e desmontagem DEVEM ser idempotentes e protegidas contra duplicação.
14. Dados futuros ou inválidos DEVEM resultar em robô inerte recuperável, não em
    migração especulativa.

## Vocabulário e camadas

### Estrutura física

Conjunto conectado de blocos permitidos ao redor de um núcleo de chassi. É a forma
editável do projeto e participa normalmente de chunks, rede elétrica e ferramentas.

### `RobotAssemblySnapshot`

Representação pura criada na thread do servidor:

```text
RobotAssemblySnapshot
  schema
  ownerId
  anchor
  facing
  modules: RobotModuleSnapshot[]
  contacts: RobotContactSnapshot[]
  board: RoboBoardState.Persisted
  fingerprint
```

O snapshot não contém `World`, `Block`, `TileEntity`, `Entity`, `ItemStack` mutável
ou callbacks Forge. Listas são ordenadas e copiadas defensivamente.

### Manifesto móvel

Forma canônica do snapshot depois da validação. Coordenadas são locais ao chassi,
IDs são estáveis e parâmetros são sanitizados. O manifesto é persistido na
entidade e não muda enquanto ela estiver ativa.

### Host robótico

Núcleo puro que contém o manifesto, `RoboBoardState`, relógio lógico, estado dos
motores, contador de medições e máquina de estados do quadro. Adaptadores Forge
fornecem snapshots do mundo e aplicam o resultado mecânico.

### Entidade robótica

Casca Forge registrada para persistência, tracking e renderização. Sua AABB é
apenas broad phase; o contrato de colisão do veículo é definido abaixo.

## Coordenadas, orientação e unidades

- Mundo: `+X` leste, `+Y` para cima e `+Z` sul.
- `yaw = 0°`: frente local aponta para `+Z`.
- Yaw positivo segue a convenção de `Entity.rotationYaw` do Minecraft.
- Vetor frontal: `(-sin(yaw), 0, cos(yaw))`.
- Vetor direito: `(cos(yaw), 0, sin(yaw))`.
- Posições e comprimentos internos: metros; `1 bloco = 1 metro`.
- Tempo mecânico: segundos.
- Velocidade linear: m/s; angular: rad/s.
- Tensão: volts; corrente: ampères; resistência: ohms.
- Tempo AVR: ciclos inteiros a 16 MHz.
- Distância acústica e CSV: centímetros.

O núcleo mecânico DEVE ser `strictfp`. Na entrada, NaN, infinito e magnitudes fora
dos limites são rejeitados. NBT persiste doubles canônicos e também `simulationFrame`
inteiro; testes comparam tolerâncias publicadas, nunca strings de doubles.

## Contrato da montagem física

### Envelope e limites

- A estrutura inteira deve caber em `5 × 3 × 5` blocos, inclusive terminais.
- O chassi define a origem local `(0,0,0)`.
- Todos os blocos devem estar no mesmo chunk e carregados.
- No máximo 32 blocos capturados e 96 contatos elétricos.
- A busca usa BFS com ordem canônica `x`, `y`, `z`, face e tipo.
- Apenas blocos presentes numa allowlist versionada podem ser capturados.
- Blocos decorativos, baús, entidades, inventários externos e cabos Redstone são
  recusados, não ignorados.

O modelo físico dos componentes em blocos é deliberadamente ampliado para ensino.
Na ativação, os módulos são renderizados numa miniatura rígida; um voxel de montagem
usa escala visual máxima de 0,35 m. A colisão e a cinemática usam o envelope do
chassi, não a união dos cubos em tamanho natural.

### Módulos obrigatórios do perfil 1

| Módulo | Quantidade | Regra |
| --- | ---: | --- |
| núcleo de chassi | 1 | âncora e identidade da montagem |
| RoboBoard | 1 | proprietário compatível com quem ativa |
| HC-SR04 | 1 | montado na metade frontal, sem obstrução no cone |
| ponte H dupla | 1 | entradas lógicas e duas saídas de motor |
| motor/roda esquerda | 1 | lado local `-X`, eixo lateral |
| motor/roda direita | 1 | lado local `+X`, eixo lateral |
| fonte móvel de 5 V | 1 | perfil educacional inicial |
| GND | 1 | referência comum |

RoboPorts, fios e suportes permitidos podem completar a estrutura. O HC-SR04 não
pode apontar para outro módulo do próprio manifesto. Rodas precisam ter orientação
oposta e compartilhar o mesmo eixo longitudinal dentro de tolerância de um voxel.

### Validação elétrica

O extrator móvel reutiliza snapshots e MNA puros, trocando `BlockPosition` por
`LocalModulePosition`. A montagem só é ativável quando:

- existe referência GND única e alimentação compatível;
- VCC/GND da placa, ponte H e HC-SR04 estão definidos;
- cada sinal exigido alcança exatamente um pino digital;
- as duas saídas da ponte chegam a motores distintos;
- não existem papéis digitais duplicados ou nós incompletos;
- nenhum terminal aponta para fora da estrutura capturada.

O circuito móvel fica imutável até a desmontagem. Estados de firmware, motores e
sensor mudam; topologia não muda.

## Pinagem de referência do primeiro robô

O cabeamento físico continua sendo a fonte de verdade, mas o laboratório, manual e
sketch oficial usam a seguinte pinagem:

| Função | Pino |
| --- | --- |
| HC-SR04 ECHO | D6 |
| HC-SR04 TRIG | D7 |
| ponte H esquerda IN1 | D2 |
| ponte H esquerda IN2 | D4 |
| ponte H esquerda EN/PWM | D5 |
| ponte H direita IN1 | D8 |
| ponte H direita IN2 | D10 |
| ponte H direita EN/PWM | D9 |

Estados da ponte H:

| IN1 | IN2 | Efeito |
| --- | --- | --- |
| LOW | LOW | roda livre, esforço zero |
| HIGH | LOW | sentido positivo |
| LOW | HIGH | sentido negativo |
| HIGH | HIGH | freio, esforço zero |

`EN` multiplica o esforço por `PWM/255`. Pino configurado como entrada, PWM inválido,
alimentação ausente ou estado indeterminado produz esforço zero e diagnóstico; não
herda o último comando válido.

## Perfil mecânico diferencial 1

Parâmetros iniciais, explícitos e educacionais:

| Parâmetro | Valor |
| --- | ---: |
| largura de colisão | 1,40 m |
| comprimento de colisão | 1,80 m |
| altura de colisão | 1,20 m |
| distância entre rodas | 1,00 m |
| velocidade linear máxima de cada roda | 1,50 m/s |
| aceleração máxima | 3,00 m/s² |
| desaceleração em roda livre | 2,00 m/s² |
| desaceleração em freio | 6,00 m/s² |
| velocidade angular máxima do chassi | 3,00 rad/s |

Para velocidades lineares das rodas `vL` e `vR`:

```text
v = (vR + vL) / 2
omega = (vR - vL) / wheelTrack
```

O modelo de CRL-60 poderá introduzir queda da ponte, tensão e carga, mas deverá
produzir `targetWheelSpeed` dentro desses limites. Não alegará reproduzir torque,
atrito ou corrente de um motor real sem ensaio e perfil documentados.

Integração usa `dt = 0,05 s` e subpassos suficientes para limitar cada avanço a
0,10 m e cada rotação a 5°. O limite é determinístico e calculado antes do quadro.

## Terreno e colisão

O perfil 1 opera sobre topo horizontal de blocos inteiros sólidos.

- Broad phase usa AABB conservadora que contém o retângulo em qualquer yaw.
- Narrow phase usa retângulo orientado no plano XZ contra AABBs de blocos.
- A varredura cobre toda a trajetória de cada subpasso; não testa apenas o destino.
- Colisão bloqueia a componente penetrante e aplica freio aos dois motores.
- O robô não causa dano, não empurra entidades e não quebra blocos.
- Outro robô é obstáculo sólido; desempate usa UUID em ordem bytewise.
- Ausência de piso, líquido, forma parcial, degrau ou desnível maior que 0,25 m
  causa parada antes da borda com `UNSUPPORTED_TERRAIN`.
- Se qualquer chunk necessário à broad phase não estiver carregado, o quadro não
  avança e publica `CHUNK_UNAVAILABLE`.

Colisão é calculada com o estado atual do servidor no momento de aplicar o quadro.
O cliente interpola poses confirmadas e nunca faz predição autoritativa.

## Quadro de simulação de 50 ms

Cada robô possui `simulationFrame`, `completedAvrCycles` e no máximo uma submissão
em voo. Um quadro segue obrigatoriamente esta máquina:

```text
READY
  -> CAPTURE_ENVIRONMENT
  -> SUBMIT_AVR_800000_CYCLES
  -> WAITING_WORKER
  -> VALIDATE_RESULT
  -> BUILD_ACTUATOR_TIMELINE
  -> INTEGRATE_PHYSICS_50_MS
  -> COMMIT_FRAME
  -> READY
```

### Captura

Na thread do servidor:

1. validar entidade, alimentação, chunks e pose;
2. copiar entradas digitais/analógicas;
3. consultar o campo acústico a partir da pose confirmada;
4. construir sequências de eco limitadas;
5. copiar checkpoint, geração e revisão.

Nenhuma referência Forge atravessa o request do worker.

### Execução AVR

O alvo do quadro é exatamente `completedAvrCycles + 800000`. O supervisor pode
dividi-lo em quanta de 50.000 ciclos, mas entradas temporais pertencem ao mesmo
snapshot do quadro. GPIO, PWM e UART retornam com ciclos absolutos.

O HC-SR04 recebe uma `UltrasonicEchoSequence` de no máximo oito respostas por
quadro. Cada borda TRIG válida consome a próxima entrada; exceder o limite produz
timeout, não reutiliza a última distância. O seed de ruído é derivado de:

```text
worldSeed, robotUUID, sensorOrdinal, measurementCounter
```

`measurementCounter` incrementa apenas quando um TRIG válido consome uma entrada e
é persistido. Carga de máquina, FPS e ordem de entidades não alteram a sequência.

### Aplicação

O servidor valida identidade, checkpoint, limites e ordem dos eventos. A linha
temporal da ponte H é reconstruída pelos ciclos dos eventos; cada intervalo aplica
seu esforço durante a fração correspondente dos 50 ms. Depois a física é integrada
e commitada atomicamente como um quadro.

Enquanto `WAITING_WORKER`, pose, contador acústico e física não avançam. No máximo
um quadro é commitado por tick do mundo, evitando rajadas de catch-up. Assim um host
lento torna o robô mais lento em relação ao mundo, mas não muda sua trajetória
lógica nem atravessa obstáculos.

## Identidade e protocolo do runtime

A identidade atual `(dimension, x, y, z, generation)` não serve para entidades
móveis. O protocolo futuro deve aceitar:

```text
RuntimeHostIdentity
  kind: STATIC_BOARD | MOBILE_ROBOT
  dimension
  hostUUID
  generation
```

Placas existentes derivam `hostUUID` do `boardId`; coordenadas continuam no
adaptador para autorização e interface, não na identidade do worker. Robôs usam
seu UUID persistente. Geração incrementa em reset, troca de firmware, unload,
desmontagem e invalidação.

O editor direcionado a robô envia dimensão, UUID, geração e revisão. O servidor
resolve somente entidades carregadas, valida proprietário, distância e revisão e
rejeita posição fornecida pelo cliente como fonte de identidade.

## Máquina de estados do robô

```text
BLOCK_ASSEMBLY
  -> VALIDATING
  -> ASSEMBLY_TRANSACTION
  -> ENTITY_STOPPED
  -> ENTITY_RUNNING <-> ENTITY_STOPPED
  -> ENTITY_FAULT
  -> DISASSEMBLY_TRANSACTION
  -> BLOCK_ASSEMBLY
```

Estados adicionais:

- `SUSPENDED`: descarregado ou runtime temporariamente indisponível;
- `RECOVERY`: NBT conhecido, mas transação incompleta;
- `QUARANTINED`: schema/dados inválidos; entidade inerte, sem drops automáticos.

Somente `ENTITY_RUNNING` agenda firmware. Somente um quadro validado pode alterar
pose. `FAULT`, `SUSPENDED`, `RECOVERY` e `QUARANTINED` zeram motores.

## Ativação transacional

A ferramenta de montagem opera apenas na thread do servidor e exige proprietário
ou operador.

1. Descobrir e validar sem mutação.
2. Produzir snapshot, manifesto, fingerprint e UUID novos.
3. Verificar espaço da entidade e ausência de jogadores/entidades no envelope.
4. Gravar `RobotConversionJournal` como `ASSEMBLY_PREPARED`.
5. Remover os blocos em ordem canônica, sem gerar drops.
6. Criar a entidade parada e inserir o manifesto.
7. Marcar journal como `ENTITY_COMMITTED` e concluir.

Falha síncrona restaura todos os blocos a partir dos snapshots. O journal contém
somente tipos allowlisted e NBT sanitizado. Na carga:

- entidade presente e fingerprint igual: garantir blocos ausentes e concluir;
- entidade ausente: restaurar blocos se todas as posições estiverem livres;
- conflito: manter journal em `RECOVERY`, não gerar itens e exigir ferramenta de
  recuperação ou operador.

Uma montagem inteira deve estar no mesmo chunk para reduzir estados de save
divergentes. O journal não autoriza carregar o chunk durante recuperação.

## Desmontagem transacional

Desmontagem exige entidade parada, sem request em voo, sobre terreno suportado e
volume completo de blocos livre e carregado.

1. Cancelar runtime, incrementar geração e zerar motores.
2. Calcular posições pelo yaw cardinal mais próximo; yaw deve estar a até 5° dele.
3. Gravar `DISASSEMBLY_PREPARED` com manifesto e estado da placa.
4. Colocar blocos sem drops e restaurar NBT sanitizado.
5. Remover entidade somente depois de todos os blocos existirem.
6. Marcar journal concluído.

Não existe desmontagem parcial. Entidade não sofre dano e não solta módulos ao
morrer no perfil 1; remoção por comando passa pelo mesmo journal.

## Persistência

`MobileRobotState` usa schema próprio e limites fixos:

```text
MobileRobotState
  schema
  robotUUID, ownerUUID
  manifest + fingerprint
  boardState
  simulationFrame, completedAvrCycles
  pose, wheelSpeeds
  measurementCounter
  status, diagnostic, resumeRequested
```

- Manifesto: máximo 32 módulos, 96 contatos e 32 KiB codificados.
- Estado de placa conserva os limites de firmware, checkpoint, sketch e Serial.
- Strings de diagnóstico usam códigos limitados, não stack traces.
- Arrays e NBT são copiados e validados antes de uso.
- Request em voo e snapshot de mundo nunca são persistidos.

No unload, o host cancela request, incrementa geração, zera motores e salva apenas
o último quadro commitado. Se estava em execução, persiste `resumeRequested=true`.
No load, fica neutro por pelo menos um tick carregado, valida chunks e manifesto e
então retoma a partir do checkpoint; wall clock descarregado não avança.

Schema futuro desconhecido cria `QUARANTINED`. Os bytes reconhecíveis são
preservados para recuperação, seguindo a política já usada pela RoboBoard.

## Sincronização cliente-servidor

O tracking da entidade publica, com frequência limitada:

- UUID e hash do manifesto;
- pose confirmada e número do quadro;
- velocidades visuais limitadas das rodas;
- flags `running`, `fault`, `powered` e `echoActivity`;
- código curto de diagnóstico.

Firmware, checkpoint, netlist, contador de ruído e histórico Serial não são
broadcast. Editor e Serial usam mensagens direcionadas ao jogador autorizado.

O cliente interpola entre duas poses confirmadas. Teleporte, mudança de dimensão,
reload ou diferença excessiva de frame faz snap explícito. Pacotes atrasados com
frame menor ou igual ao último aplicado são ignorados.

## Autoridade e segurança

- Somente proprietário, operador ou política de turma pode editar/ativar/desmontar.
- Interação cliente contém intenção e identidade; estado calculado é ignorado.
- Distância de interação, dimensão, entidade carregada, geração e revisão são
  validadas em toda mutação.
- Um jogador tem no máximo uma operação de conversão pendente.
- Descoberta, módulos, eventos AVR, ecos, subpassos e bytes de rede têm limites.
- Nenhuma falha de um robô para o runtime de outro ou para a thread do servidor.
- Entidade inválida não executa firmware, não se move e não produz drops.

## Diagnósticos mínimos

| Código | Resultado seguro |
| --- | --- |
| `ASSEMBLY_TOO_LARGE` | nenhuma mutação |
| `ASSEMBLY_CROSSES_CHUNK` | nenhuma mutação |
| `MISSING_REQUIRED_MODULE` | nenhuma mutação |
| `DUPLICATE_MODULE` | nenhuma mutação |
| `INVALID_WIRING` | nenhuma mutação |
| `SENSOR_OBSTRUCTED` | nenhuma mutação |
| `SPAWN_OBSTRUCTED` | nenhuma mutação |
| `CHUNK_UNAVAILABLE` | motores zero, quadro pendente não criado |
| `UNSUPPORTED_TERRAIN` | parada antes do risco |
| `RUNTIME_BUSY` | motores zero; retry limitado |
| `RUNTIME_FAULT` | estado `FAULT`, motores zero |
| `INVALID_RUNTIME_RESULT` | estado `FAULT`, geração incrementada |
| `COLLISION_BLOCKED` | movimento limitado, freio |
| `RECOVERY_REQUIRED` | estado inerte, sem drops |
| `UNSUPPORTED_SCHEMA` | `QUARANTINED` |

Ausência de eco é dado normal do HC-SR04 e não diagnóstico de plataforma. O sketch
recebe timeout de `pulseIn()` e decide seu comportamento seguro.

## Contrato acústico móvel

O sensor usa a pose confirmada no início do quadro. O ponto de emissão e cone são
transformados pelo manifesto e yaw da entidade. A consulta:

- alcança 2–400 cm;
- considera somente chunks carregados;
- considera blocos e entidades allowlisted como refletores;
- usa cone, incidência, área aparente e perfil acústico;
- pode produzir eco, viés, dispersão ou timeout;
- mantém MDF/plástico mais repetíveis que isopor/espuma;
- não revela ao sketch material ou distância verdadeira.

O trilho sub-bloco de CRL-55 continua sendo modo de bancada fixa e não participa
da entidade móvel. O robô mede geometria real do mundo em escala `1 bloco = 1 m`.

## Ordem canônica com múltiplos robôs

No início de cada tick, entidades carregadas são ordenadas por UUID. Cada uma pode:

- capturar ou submeter um quadro;
- coletar resultado pronto;
- commitar no máximo um quadro.

Para colisão robô-robô, o menor UUID é integrado primeiro e o segundo observa a
pose já commitada. A regra é simples, pública e determinística; não pretende
simular conservação de momento.

## Migração da implementação atual

### CRL-58 — sensor móvel

- Extrair consulta acústica pura baseada em `SensorPose`.
- Implementar cone, área aparente, blocos e entidades.
- Preservar adaptador cardinal de bloco e bancada existente.

### CRL-59 — entidade e estado

- Criar `MobileRobotState`, entidade Forge e protocolo visual.
- Implementar NBT, unload e entidade inerte sem movimento.

### CRL-60 — ponte H e mecânica

- Adicionar snapshots elétricos e modelo diferencial puro.
- Implementar colisão e terreno do perfil 1.

### CRL-61 — host runtime

- Extrair agendamento de `TileEntityRoboBoard` para host reutilizável.
- Migrar identidade do protocolo e executar quadros de 800.000 ciclos.
- Implementar sequência acústica e timeline de atuadores.

### CRL-62 — conversão física

- Implementar allowlist, extrator, journal, ferramenta e desmontagem.

CRL-63 a CRL-67 validam metrologia, arena, autonomia, multiplayer e release.

## Gates obrigatórios

### Testes puros

- canonicalização independente de ordem de entrada;
- rejeição de envelope, módulos e wiring inválidos;
- cinemática reta, curva, ré, giro e freio;
- subpassos e colisão sem tunneling;
- frame determinístico sob latência artificial do worker;
- sequência de ruído estável por UUID e contador;
- migração e rejeição de NBT;
- recuperação idempotente dos dois journals.

### Integração Forge

- montar, ativar, salvar, recarregar e desmontar;
- unload durante runtime e durante conversão;
- tentativa em fronteira de chunk e em volume obstruído;
- editor, compilação, Serial e autorização na entidade;
- servidor dedicado com dois clientes;
- dois robôs em rota de colisão;
- nenhum carregamento de chunk por sensor ou física.

### Gate educacional final

Um sketch Arduino com um HC-SR04 e a pinagem de referência deve conduzir o robô por
uma arena rígida reproduzível. Trocar obstáculos por espuma deve aumentar dispersão,
timeouts ou falhas de navegação de modo observável. Desconectar sensor, alimentação
ou ponte H deve produzir parada segura e diagnóstico, nunca movimento inventado.

## Questões adiadas

- Perfil de bateria e queda de tensão dinâmica.
- Interferência entre vários HC-SR04 simultâneos.
- Terreno parcial e suspensão.
- Danos, drops e recuperação por item.
- Mais sensores e atuadores.
- Física de empurrão e acoplamento entre entidades.

Essas questões não bloqueiam o robô diferencial inicial e não devem ampliar seu
escopo durante CRL-58 a CRL-62.
