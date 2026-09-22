# Juntas modulares

O perfil inicial fornece dois blocos de um grau de liberdade:

- **dobradiça robótica ±90°** (`craftonica:revolute_joint`), com coordenada em
  radianos, torque máximo de 0,5 N·m e batentes em `[-π/2, π/2]`;
- **trilho linear robótico 1 m** (`craftonica:prismatic_joint`), com coordenada em
  metros, força máxima de 100 N e batentes em `[-0,5, 0,5]`.

A face clicada ao colocar o bloco define o lado pai. A face oposta define o lado
filho. A pose completa do bloco deriva o eixo da dobradiça ou do trilho, inclusive
em orientações verticais. O conector mecânico lateral aceita o caminho físico de
transmissão; mera proximidade não aplica esforço.

## Extração e validação

Conexões estruturais comuns são agrupadas em corpos rígidos. As duas portas da
junta interrompem essa união e criam uma aresta pai–filho. Antes da conversão, o
servidor recusa explicitamente:

- porta pai ou filho ausente ou duplicada;
- conexão rígida contornando a junta;
- corpo com múltiplas juntas pai, ciclo ou corpo órfão;
- mais de uma conexão mecânica na entrada da mesma junta;
- mais de 32 corpos ou 31 juntas.

Essa validação ocorre antes da transação que remove blocos do mundo, portanto uma
montagem inválida não cria entidade parcial nem duplica itens.

## Batentes e estado

O integrador de coordenada usa esforço e carga em subpassos de no máximo 25 ms.
Ele limita esforço e velocidade, aplica atrito viscoso e produz reação de batente
finita. A coordenada nunca é escrita diretamente no alvo do servo e não atravessa
os limites.

Posição e velocidade de cada junta são persistidas no envelope checksummed do
robô, ordenadas pela posição local do bloco e validadas novamente no carregamento.
O payload de criação da entidade envia ao cliente somente essa projeção limitada;
o cliente não possui API para enviar estado físico de volta ao servidor.

## Fronteira desta etapa

O CRL-86 entrega blocos, topologia, coordenadas, batentes e a entrada física para
servo/transmissão. A composição das poses dos corpos, colisão articulada e aplicação
do esforço ao mecanismo completo pertencem ao solver do CRL-87. Até esse solver,
as juntas persistem em repouso e nenhuma animação visual é usada como autoridade.
