# Encoders, fins de curso e IMU móvel

Os sensores móveis são amostrados exclusivamente pelo servidor a partir do estado
físico confirmado. Todos exigem `vcc`, `gnd`, portas reais até o RoboBoard e ficam
ausentes — nível baixo ou 0 V — quando a alimentação ou pinagem não é válida.

## Encoder incremental

O encoder de 20 PPR é instalado **em série** no eixo por `shaft_in` e `shaft_out`.
O analisador mecânico registra a relação e o sentido exatamente no ponto onde ele
aparece na transmissão. Assim, um encoder antes e outro depois de uma redução não
recebem a mesma velocidade por conveniência.

As saídas `channel_a` e `channel_b` devem chegar a dois pinos digitais distintos.
São publicados quatro estados de quadratura por pulso (80 bordas por volta). O
perfil aceita no máximo uma borda observável por amostra de 50 ms. Movimento mais
rápido satura e perde contagens em vez de fornecer odometria perfeita. Fase e
contagem observada persistem no envelope checksummed.

## Fim de curso

O fim de curso usa a face `forward` como direção do êmbolo. A cada amostra o servidor
transforma sua pose pelo corpo articulado correspondente e testa o curso publicado de 4 cm
na ponta. Somente chunks já carregados são consultados. Contato produz nível alto em
`signal`; fronteira descarregada ou fiação inválida produz nível baixo e diagnóstico,
nunca carrega o chunk nem inventa contato.

## IMU educacional

A IMU fornece três sinais analógicos:

- `gyro_z`: yaw, faixa de ±250 graus/s;
- `accel_x`: aceleração no eixo direito instalado, faixa de ±2 g;
- `accel_z`: aceleração no eixo frontal instalado, faixa de ±2 g.

Zero físico corresponde a 2,5 V. Os sinais são limitados a 0–5 V, quantizados em
1024 níveis e recebem ruído determinístico de até 0,3% da faixa. Fora da faixa a
leitura satura e o robô publica diagnóstico. A aceleração é diferença de velocidade
entre amostras confirmadas; a primeira leitura após carga retorna aceleração zero.

## API Arduino limitada

`CraftonicaSensors.h` fornece:

- `CraftonicaEncoder(a, b)`, `begin()`, `update()`, `read()` e `zero()`;
- `craftonicaLimitPressed(pin)`;
- `CraftonicaImu3(gyro, ax, az)` com leituras em rad/s e m/s².

A biblioteca apenas interpreta `digitalRead` e `analogRead`; ela não acessa o mundo,
não corrige montagem e não cria um barramento oculto. Pinos duplicados entre sensores
são recusados na amostra.

## Limites

- até 32 sensores móveis por robô;
- encoders observam eixos rotativos suportados pelo grafo de transmissão;
- a IMU inicial expõe yaw e aceleração planar, não pitch/roll ou magnetômetro;
- vibração estrutural, elasticidade do suporte e bounce elétrico detalhado ficam fora
  do perfil inicial;
- cliente recebe apenas diagnósticos visuais, nunca autoridade sobre leituras.
