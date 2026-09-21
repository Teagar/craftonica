# Modelo educacional de motor CC modular

Este documento descreve a aproximação usada no núcleo modular 2.0. Todas as
grandezas mecânicas usam SI. O modelo é determinístico e autoritativo no servidor.

## Equações por subpasso

Para velocidade do rotor `ω` em rad/s:

```text
backEmf = Ke * ω
current = clamp((terminalVoltage - backEmf) / (Rmotor + Rbridge), ±Imax)
electromagneticTorque = Kt * current
shaftTorque = electromagneticTorque - b * ω
angularAcceleration = (shaftTorque - loadTorque) / J
ωnext = clamp(ω + angularAcceleration * dt, ±ωmax)
```

A ponte em `FORWARD` ou `REVERSE` aplica a tensão da fonte menos a queda da
ponte, multiplicada pelo duty cycle PWM. `BRAKE` fecha o motor sobre a resistência
de frenagem e permite corrente oposta causada pela back-EMF. `COAST` abre o
circuito: corrente e torque eletromagnético são zero, restando apenas atrito.

## Perfil padrão

| Grandeza | Valor |
| --- | ---: |
| resistência de armadura | 4 Ω |
| `Kt` | 0,04 N·m/A |
| `Ke` | 0,04 V·s/rad |
| inércia do rotor | 0,0002 kg·m² |
| atrito viscoso | 0,0001 N·m·s/rad |
| corrente máxima | 1 A |
| velocidade máxima | 300 rad/s |
| queda da ponte | 0,8 V |
| resistência ligada/freio | 0,2 Ω / 0,35 Ω |

As perdas `I²R` aquecem motor e ponte; resfriamento é linear para a temperatura
ambiente. Ao alcançar 120 °C no motor ou 110 °C na ponte, o canal entra em
desligamento térmico latched e aplica `COAST` seguro.

## Aproximações declaradas

- PWM é a média de tensão durante o subpasso; ripple e indutância são omitidos.
- Escovas, saturação magnética, cogging e desgaste não são simulados.
- O clamp de corrente representa proteção eletrônica ideal, não fonte infinita.
- Torque só alcançará uma roda quando o grafo mecânico posterior confirmar
  motor–eixo–cubo; proximidade nunca cria transmissão.
