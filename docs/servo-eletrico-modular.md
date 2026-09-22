# Servo elétrico modular

O servo educacional modular é um bloco físico com portas `vcc`, `gnd`, `signal` e
`output`. Ele só produz esforço quando alimentação, retorno e RoboPort digital
existem na netlist capturada. A proximidade de uma RoboBoard não cria sinal.

## API Arduino publicada

```cpp
#include <Servo.h>

Servo arm;

void setup() {
  arm.attach(9);       // apenas D9 ou D10
  arm.write(90);       // alvo de 90 graus
}

void loop() {
  arm.writeMicroseconds(1750);
  delay(20);
}
```

Métodos suportados: `attach(pin)`, `attach(pin,min,max)`, `detach()`, `write()`,
`writeMicroseconds()`, `read()`, `readMicroseconds()` e `attached()`. A faixa física
continua limitada a 1000–2000 us e 0–180°. D9 e D10 compartilham Timer1; cada canal
só pode pertencer a um objeto por vez.

O exemplo completo está em
[`examples/arduino/servo_modular/servo_modular.ino`](../examples/arduino/servo_modular/servo_modular.ino).

O sinal normativo é Timer1 fast PWM, prescaler 8, TOP 39999 e período de 20 ms.
O servidor confere modo, prescaler, TOP, compare, direção do pino e estado RUNNING.
`analogWrite()`, PWM em outro timer ou registradores parcialmente configurados não
viram comando de servo.

## Modelo físico

O pulso válido define referência, nunca posição instantânea. A cada subpasso de no
máximo 25 ms, um controlador PD pede torque limitado por tensão, corrente e torque
do catálogo. Inércia, velocidade máxima, atrito viscoso e carga externa determinam
a nova velocidade e posição por integração semi-implícita.

Perfil inicial:

- alimentação nominal: 5 V;
- curso: 0–180°, posição segura 90°;
- deadband: 1°;
- torque máximo: 0,22 N·m;
- velocidade máxima: 300°/s;
- corrente máxima: 1,2 A;
- timeout de sinal: 100 ms;
- proteção térmica: 65 °C, rearme abaixo de 55 °C.

Stall mantém erro, torque e corrente; portanto aquece e pode abrir a proteção. Um
batente não faz corrente desaparecer. Cortar VCC/GND remove torque imediatamente,
mas não teleporta o eixo.

## Perda de sinal

O modelo expõe três políticas explícitas para o perfil que consumir o servo:

- `HOLD`: conserva o último alvo enquanto houver energia;
- `COAST`: desliga o controlador e deixa carga/atrito moverem o eixo;
- `SAFE_POSITION`: passa a buscar 90° fisicamente.

Componente sem alimentação sempre fica sem torque, independentemente da política.
Pulso fora da faixa é diagnosticado e não substitui o último alvo válido.

## Limitação desta etapa

O CRL-85 entrega componente, fiação, API temporal e atuador físico isolado. A porta
`output` ainda não move módulos por conta própria: juntas rotativas/lineares e o
acoplamento do torque ao grafo cinemático pertencem ao CRL-86. Não há animação usada
para esconder essa fronteira.
