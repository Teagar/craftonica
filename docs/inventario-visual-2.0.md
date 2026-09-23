# Inventário visual 2.0

Todos os componentes públicos possuem nome em `pt_BR` e `en_US`, item na aba
Craftônica e textura instalada no JAR. A renderização modular usa os mesmos blocos
que o jogador coloca; não existe modelo secreto de blueprint.

## Assets finais próprios

| Família | Assets principais | Estado |
|---|---|---|
| Elétrica DC | fios, terminais, fonte, GND, botão, resistores, LED, diodo | final 16×16 |
| RoboBoard | RoboBoard, RoboPort, configurador e instrumentos | final 16×16 |
| Drive modular | chassi, ponte H, motor, eixo, mancal, engrenagens 12T/36T | final 16×16 |
| Contato | rodas 100/150 mm, rodízio, esteira, omni e mecanum L/R | final 16×16 |
| Mecanismos | servo rotativo/linear e juntas | textura funcional final; geometria conservadora por bloco |
| Sensores | HC-SR04, encoder, fim de curso e IMU | final 16×16 |
| Entidades | atlas do robô legado 1.2 | preservado apenas para `LEGACY_INERT` |

Eixo, mancal, engrenagens, servo, rodas e rodízio deixaram de reutilizar texturas
de ponte H, motor ou chassi no fechamento 2.0. Os PNGs são autorais e gerados sem
material externo. `scripts/generate-textures.sh` documenta a origem procedural dos
assets básicos; os demais PNGs versionados são a fonte canônica.

## Convenções visuais

- cobre/dourado: transmissão elétrica ou mecânica ativa;
- ciano/verde: placa, sinal ou diagnóstico normal;
- cinza/aço: estrutura e transmissão passiva;
- preto/borracha: contato com o solo;
- vermelho: polaridade, falha ou lado diferenciado;
- padrões e formas acompanham cor para não depender apenas de visão cromática.

## Limites declarados

Minecraft 1.7.10 não usa JSON block models modernos. Os componentes são renderizados
por cubos orientados, renderizadores Forge legados e AABBs compostas. Textura não
representa dentes engrenando, deformação de pneu, curso contínuo ou precisão
metrológica; pose, colisão e esforço vêm do estado servidor-autoritativo.
