# Sensores modulares móveis

Cada HC-SR04 móvel é identificado pela posição local do módulo. A origem acústica
é o centro do bloco deslocado `0,251 m` na direção frontal instalada. A pose mundial
é calculada por `robotPose * moduleTransform * emitterOffset`; não existe uma
“frente do robô” implícita.

O sensor só é habilitado quando VCC, GND, TRIG e ECHO possuem caminhos físicos
válidos na netlist e TRIG/ECHO chegam a RoboPorts digitais distintos. Sem qualquer
desses caminhos, o runtime não cria o periférico e o firmware observa ausência de
eco.

## Múltiplos sensores e crosstalk

O runtime AVR 2.0 inicial processa um periférico ultrassônico por quadro. A política
é determinística:

1. sensores são ordenados pela posição local `(x,y,z)`;
2. se um ou mais TRIG permanecem ativos no snapshot confirmado, o primeiro é
   selecionado;
3. se nenhum TRIG está ativo, o primeiro sensor habilitado é anexado para poder
   observar uma nova borda de disparo durante o quadro;
4. disparos simultâneos selecionam apenas o primeiro e retornam o estado
   `CROSSTALK_SERIALIZED`; nenhum dos demais recebe uma distância inventada;
5. mais de oito sensores habilitados retornam `ACTIVE_LIMIT_EXCEEDED` e nenhum eco.

Esta é uma serialização educacional, não um modelo de propagação acústica entre
transdutores. Alternância arbitrária de vários sensores no mesmo quadro e crosstalk
analógico permanecem explicitamente não suportados.

O raycast reutiliza o cone acústico existente, exclui a própria entidade e recorta
cada raio antes do primeiro chunk descarregado. Nenhuma medição carrega chunks.
