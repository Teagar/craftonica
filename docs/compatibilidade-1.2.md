# Compatibilidade e limitações 1.2

## Plataforma

| Item | Suporte |
|---|---|
| Minecraft Java | **1.7.10** |
| Forge | **10.13.4.1614** |
| JVM do jogo e build | **Java 8** |
| Cliente/servidor | Linux x86_64; sandbox requer bubblewrap e systemd user |
| Servidor dedicado | Classes autoritativas sem linkage client-only; roteiro manual publicado |
| Multiplayer | Estado decidido no servidor e pose rastreada pelo Forge; teste real de dois clientes continua manual |

## Perfil Arduino-compatible

| Recurso | Estado 1.2 |
|---|---|
| `setup()`, `loop()`, GPIO digital | Suportado |
| `analogRead()`, PWM por `analogWrite()` | Suportado no perfil documentado |
| `millis()`, `micros()`, `delay()` | Tempo virtual determinístico |
| `pulseIn()` | Suportado para o ECHO temporizado do HC-SR04 |
| Serial | TX apenas, histórico de 8 KiB; RX ausente |
| `tone()` e áudio do buzzer | Ausente |
| Servo | Ausente |
| Ponte H | Suportada somente no robô diferencial, com pinagem fixa |
| Bibliotecas arbitrárias, rede e filesystem | Proibidos pelo perfil e sandbox |

A referência completa da linguagem e sandbox está em
[`firmware-compiler.md`](firmware-compiler.md).

## Física móvel

| Aspecto | Modelo |
|---|---|
| Tração | Duas rodas, máximo 1,5 m/s e aceleração 3 m/s² |
| Integração | 2 subpassos de 25 ms por tick; servidor autoritativo |
| Colisão | Caixa Minecraft horizontal; sem suspensão, derrapagem ou inclinação |
| Terreno | Superfície horizontal carregada; borda, vazio ou chunk ausente causa parada |
| Energia | Ponte H educacional com PWM, direção, freio e zona morta; sem bateria química ou curva de torque real |
| Capacidade | 4 workers ativos + fila de 64; excesso retorna indisponibilidade limitada |

## HC-SR04: simulador versus hardware

O sensor virtual conserva o contrato elétrico VCC/GND/TRIG/ECHO, pulso mínimo,
tempo de voo, faixa de 2–400 cm, cone de 15 graus, incidência, tamanho aparente,
materiais e ausência de eco. MDF e plástico são mais repetíveis; isopor e espuma
têm maior viés, dispersão e timeouts.

Não são modelados temperatura e umidade do ar, tolerâncias por unidade, ringing do
transdutor, interferência entre sensores, ruído da fonte, flexão do chassi ou
reflexões acústicas contínuas completas. Os resultados ensinam aquisição, filtros
e tratamento de falhas; não substituem ensaio ou calibração de hardware real.

## Limitações operacionais conhecidas

- O editor permanece na RoboBoard fixa; o firmware é copiado para o robô por
  comando validado.
- Só existe um formato de chassi diferencial e uma montagem canônica de oito
  módulos; braços, voo e física genérica estão fora do escopo.
- O comando `/craftonica robot drive` é ferramenta temporária de diagnóstico.
- A arena é determinística, mas a travessia completa depende do sketch, dos ecos e
  da posição inicial; timeout não é convertido em distância perfeita.
- A matriz com duas contas/clientes deve ser executada manualmente conforme
  [`validacao-runtime-movel.md`](validacao-runtime-movel.md).
