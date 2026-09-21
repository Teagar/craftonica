# Acoplamento eletromecânico modular

O laço modular processa apenas quadros de controle confirmados e sequenciais. Um
quadro não disponível, fora de ordem ou acima do orçamento é atrasado por inteiro:
nenhum estado térmico, elétrico ou mecânico parcial é publicado e a sequência não
avança.

Para cada canal, em ordem canônica da posição da ponte H:

1. valida-se a ligação elétrica física ponte–motor;
2. valida-se a cadeia mecânica motor–eixo–cubo;
3. projeta-se a velocidade do contato na direção de rolamento e calcula-se `ω=v/r`;
4. `backEmf = Ke * ω` fecha o retorno mecânico ao modelo elétrico;
5. corrente e torque respeitam limites do motor e da ponte H;
6. `F=torque/r` gera força somente no contato apoiado;
7. a força é limitada pelo elo mecânico e por `mu * normal`;
8. a carga efetivamente transmissível retorna ao quadro seguinte.

O perfil aceita até 16 canais. Cada orçamento por robô permite um quadro AVR, uma
avaliação lógica de rede, até 64 passos de drive e quatro subpassos físicos por
tick. Saturação causa atraso explícito, nunca expansão de fila ou salto de quadro.
Por dimensão, no máximo 64 robôs são admitidos a cada tick por uma janela circular
sobre a ordem canônica de UUID, indexada pelo tick. Os demais preservam o estado e
recebem oportunidade determinística nos ticks seguintes, sem fila persistente.

A RoboBoard capturada é executada pelo mesmo host AVR assíncrono e limitado usado
pela plataforma fixa. Somente um resultado validado e commitado vira quadro de
controle. Neste cartão as entradas digitais/analógicas são explicitamente vazias;
as poses e retornos reais dos sensores são adicionados pelo `CRL-79`.

PWM continua sendo tensão média por subpasso. Indutância, ripple de comutação,
deformação de pneu e elasticidade de eixo permanecem aproximações omitidas já
declaradas nos modelos elétrico e terrestre.
