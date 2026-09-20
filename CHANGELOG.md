# Changelog

## Unreleased

- Multimetro com tela vanilla configuravel, pontas visuais, cabos no mundo e modelo renovado.
- Configurador de RoboPort com tela para selecionar explicitamente D0-D13, A0-A5, 5 V e GND.
- Resistores com corpo e aneis fisicos consistentes no topo, base e faces laterais.
- LED apaga e reacende corretamente ao abrir e fechar o botao eletrico.
- LEDs redesenhados como cubos de vidro translucido com emissor interno e polaridade nas faces.
- Laboratorio HC-SR04 com pulsos TRIG/ECHO reais no runtime AVR, resposta por material, trilhos de 5-50 cm e CSV pelo Serial.
- RFC da plataforma robotica movel, cobrindo montagem, entidade, runtime, tracao diferencial, persistencia e seguranca.
- HC-SR04 com pose continua, cone acustico, tamanho aparente e deteccao de blocos e entidades em chunks carregados.

## 1.1.0 - 2026-09-19

- RoboPorts remotos vinculados por configurador, com D0-D13, A0-A5, 5 V e GND.
- Roteamento de fios por face, heranca de cor e tingimento com as 16 cores de la.
- LEDs nas 16 cores de la, com estados desligado, ligado e queimado distinguiveis.
- Semaforo funcional no showcase, controlado independentemente por D10-D12.
- Gerador `/craftonica uno create` para uma placa Uno R3 funcional com 22 terminais.
- Sincronizacao visual imediata das cores dos LEDs criados pelos geradores.

## 1.0.0 - 2026-09-19

- Simulacao nodal DC servidor-autoritativa para redes serie/paralelo.
- Instrumentacao, protecao, componentes educacionais e laboratorios verificaveis.
- RoboBoard Arduino-compatible com compilacao/runtime isolados e Serial TX.
- Persistencia versionada, backup pre-migracao e rollback verificavel.
- Ownership multiplayer, limites por jogador e cancelamento em desconexao.
- Orcamentos de desempenho, GUI responsiva e alternativas nao dependentes de cor.
- Instalacao reproduzivel para cliente Prism e servidor dedicado.
