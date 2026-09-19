# Gerador Arduino Uno R3

Em um mundo superplano, entre como operador e execute:

```text
/craftonica uno create
```

O comando substitui uma área delimitada próxima ao jogador, constrói uma placa
inspirada visualmente no Arduino Uno R3 e move o jogador para sua entrada. A placa
mede `13 x 19` blocos e inclui corpo azul, headers, USB, alimentação, microcontrolador,
cristal, reset, indicadores e uma RoboBoard central.

Os 22 RoboPorts são funcionais e pertencem à mesma RoboBoard: `D0-D13`, `A0-A5`,
`POWER_5V` e `GROUND`. Cada terminal mostra seu papel em uma placa e conecta o
circuito somente pela face externa. Executar o comando novamente na mesma origem
restaura deterministicamente a placa.
