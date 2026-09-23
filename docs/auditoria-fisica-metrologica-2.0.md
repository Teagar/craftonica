# Auditoria física e metrológica 2.0

Esta auditoria separa três coisas que não devem ser confundidas:

1. **equações públicas**, reproduzidas pelo gate;
2. **parâmetros do perfil educacional**, escolhidos pelo catálogo da Craftônica;
3. **medições de hardware**, que exigem ensaio e calibração por unidade e não são
   prometidas pelo simulador.

Execute com Java 8:

```bash
./scripts/gradle-java8.sh physicsAudit
```

O resultado fica em `build/reports/runtime/crl-96-physics.csv`. O baseline
versionado está em [`data/fidelidade-fisica-2.0.csv`](data/fidelidade-fisica-2.0.csv).

## Referências públicas

| ID no CSV | Referência | Uso auditado |
|---|---|---|
| `dc_motor_equations` | [MathWorks — DC Motor](https://www.mathworks.com/help/sps/ref/dcmotor.html) | `T=Kt·I`, `Vbemf=Ke·ω`, corrente da armadura |
| `spur_gear_ratio` | [RoyMech — Spur Gears](https://roymech.org/Useful_Tables/Drive/Gears.html) | razão por dentes, velocidade, torque e inversão externa |
| `rolling_kinematics` | [OpenStax University Physics — Rolling Motion](https://openstax.org/books/university-physics-volume-1/pages/11-1-rolling-motion) | condição sem escorregamento `v=ωr` |
| `coulomb_friction` | [OpenStax University Physics — Friction](https://openstax.org/books/university-physics-volume-1/pages/6-2-friction) | limite estático `F≤μN` |
| `arduino_servo_pulse` | [Arduino Servo.writeMicroseconds](https://docs.arduino.cc/libraries/servo/#Servo.writeMicroseconds) | pulso como comando de posição; endpoints variam por servo |
| `hc_sr04_datasheet` | [HC-SR04 user manual](https://web.eece.maine.edu/~zhu/book/lab/HC-SR04%20User%20Manual.pdf) | 5 V, TRIG ≥10 µs, 2–400 cm, conversão do ECHO |

Referências consultadas em 22 de setembro de 2026. Elas sustentam as equações e
faixas, não os parâmetros próprios do catálogo.

## Resultados e faixas válidas

- **Motor CC:** corrente, torque e velocidade estacionária concordam com o modelo
  linear dentro da tolerância do CSV. Válido para PWM médio e passos ≤50 ms.
- **Transmissão:** 12T→36T resulta em `-1/3` da velocidade e `3×0,96` do torque.
  Válido para árvore rígida de engrenagens retas, sem folga.
- **Roda e atrito:** a cinemática usa `v=ωr`; tração é limitada por `μN`. Os
  coeficientes 0,9/0,7 são parâmetros educacionais, não medições de um pneu.
- **Servo:** 1000/1500/2000 µs mapeiam para alvos de 0/90/180°. O alvo não
  teleporta a junta. O torque de 0,22 N·m e a velocidade de 300°/s são limites do
  perfil, não especificação de uma marca ou garantia de posição.
- **HC-SR04:** a aproximação de 58 µs/cm difere cerca de 0,53% do percurso de ida
  e volta calculado com 343 m/s. O modelo virtual limita 2–400 cm e quantiza o ECHO
  em ciclos AVR; ruído e dropout são sintéticos e determinísticos.

## Fenômenos omitidos

| Subsistema | Omissões relevantes |
|---|---|
| Motor/ponte | indutância, ripple de comutação, escovas, cogging, saturação, bateria química e dispersão de fabricação |
| Transmissão | backlash, elasticidade, vibração, desgaste, lubrificação, quebra e divisão de potência |
| Roda/solo | deformação de pneu, suspensão, transferência dinâmica de carga, pitch/roll, aquaplanagem e conservação de momento entre robôs |
| Servo | folga, potenciômetro real, jitter, curva torque-velocidade do fabricante, engrenagens e erro absoluto do horn |
| Ultrassom | temperatura/umidade, tolerância entre unidades, ringing, multipercurso contínuo, crosstalk acústico e geometria do transdutor |

Portanto, a Craftônica é adequada para ensinar causalidade, unidades, saturação,
falhas e comparação de projetos. Ela não substitui datasheet específico, bancada,
instrumento calibrado nem validação de segurança de um robô real.
