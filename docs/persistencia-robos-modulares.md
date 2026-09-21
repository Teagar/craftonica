# Persistência e migração de robôs

## Envelope modular 2

`CraftonicaModularRobotV2` contém um envelope com tipo, schema, payload e SHA-256.
O checksum é calculado sobre a codificação binária NBT, incluindo o conteúdo de
arrays de firmware, checkpoint e histórico serial. O payload é limitado a 1 MiB.

O payload salva como uma unidade:

- identidade, proprietário, âncora, status, manifesto, netlist e fingerprint;
- pose, velocidades linear e angular do corpo rígido;
- sequência, rotação, carga, corrente, temperaturas, desligamento térmico e
  diagnóstico de cada canal eletromecânico;
- estado completo da RoboBoard, incluindo firmware e checkpoint já verificados;
- contador determinístico dos sensores.

O quadro AVR já confirmado, mas ainda não aplicado, é deliberadamente transitório.
Ao recarregar, o próximo commit confirmado recria o quadro na mesma sequência do
solver; nenhum quadro persistido é aplicado duas vezes.

## Migração e quarentena

O formato de desenvolvimento modular 1 (campos separados na entidade) é lido e
reescrito uma única vez no envelope 2. Schema futuro, checksum incorreto, limites
violados ou estado interno incompatível não removem a entidade e não criam drops:
o robô recebe `QUARANTINED`, fica inerte e o envelope original é preservado para
ferramentas futuras de recuperação. Robôs em quarentena não podem ser desmontados
a partir do manifesto substituto de segurança. Um payload que já exceda 1 MiB é
substituído por um marcador limitado com a causa, pois regravar dados arbitrariamente
grandes violaria o limite de segurança.

Entidades fixas públicas da versão 1.2 (`mobile_robot:1`) não são convertidas para
uma montagem física, pois essa conversão perderia topologia. Ao ler schema 1 elas
passam uma única vez para schema 2 com status `LEGACY_INERT`, preservando identidade,
manifesto, pose, firmware e checkpoint. O formato continua legível e visível, mas
não executa física nem AVR no runtime modular.

No unload de chunk, jobs locais são cancelados e filas transitórias são descartadas;
posição, energia representada, estado térmico, sequência, checkpoint e contadores
persistidos não avançam fora de um tick admitido.
