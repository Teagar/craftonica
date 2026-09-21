# Robô móvel: montagem guiada

Este roteiro parte de um mundo criativo novo e termina com um sketch Arduino real
controlando um robô diferencial. A simulação executa no servidor; o cliente apenas
desenha e envia interações validadas.

## 1. Prepare a arena e a bancada

1. Crie um mundo superplano em Criativo e obtenha uma chave inglesa.
2. Execute `/craftonica arena create`. O comando leva o jogador à origem verde.
3. Coloque uma RoboBoard fixa fora dos corredores.
4. Execute `/craftonica robot sketch autonomous` olhando a área de trabalho.
5. Abra a RoboBoard, leia o sketch e pressione `Ctrl+S`. Só prossiga quando a
   compilação terminar sem diagnóstico.

## 2. Monte os oito módulos

Todos os módulos devem apontar na mesma direção do núcleo. Considerando a frente
do robô para o norte, a montagem ocupa duas camadas:

```text
camada superior (y + 1)       camada inferior (y)

          [HC-SR04]                 [vazio]
          [RoboBoard]       [motor E] [chassi] [motor D]
[fonte 5 V] [ponte H] [GND]          [vazio]
             traseira
```

O HC-SR04 fica no centro frontal; a ponte H, no centro traseiro. Fonte e GND
ocupam os lados esquerdo e direito da ponte, com os terminais voltados para ela.
Se a frente não for norte, gire todo o desenho junto, sem espelhar os lados.

Use a chave inglesa no núcleo. O chat informa o primeiro módulo ausente ou mal
orientado e suas coordenadas. Quando os oito módulos são válidos, os blocos são
convertidos transacionalmente em uma única entidade; não há TileEntity em
movimento nem duplicação de peças.

## 3. Instale e execute o firmware

Mantenha a RoboBoard compilada e o robô em até 16 blocos do jogador:

```text
/craftonica robot firmware copy
/craftonica robot firmware start
/craftonica robot firmware status
```

O estado esperado é `RUNNING`. A pinagem é:

| Função | Pinos |
|---|---|
| Motor esquerdo | direção D2/D4, PWM D5 |
| HC-SR04 | ECHO D6, TRIG D7 |
| Motor direito | direção D8/D10, PWM D9 |

Abra a aba Serial da RoboBoard com `F6` para acompanhar a telemetria. `NA` indica
ausência de eco; nunca trate `NA` como zero nem reutilize silenciosamente a medida
anterior. O sketch exige dois ecos entre três tentativas e usa a mediana.

## 4. Observe e diagnostique

- Paredes de MDF e plástico tendem a leituras mais lineares.
- Isopor e espuma aumentam viés, dispersão e perda de eco.
- Alvos inclinados ou pequenos podem não devolver eco mesmo dentro de 400 cm.
- Sem eco, o robô para, varre e avança somente pelo comportamento seguro explícito
  do sketch; o sensor não enxerga através de chunks descarregados.
- Entidade avermelhada indica `FAULT` ou `QUARANTINED`; cinza indica `SUSPENDED`.

Use `/craftonica robot firmware stop` antes de desmontar. Espere as rodas pararem,
centralize o robô em um bloco, alinhe-o a um eixo cardinal e use a chave inglesa.
Os oito espaços e chunks precisam estar livres e carregados.

## 5. O que este laboratório mede

O HC-SR04 virtual reproduz faixa, tempo de pulso, cone, incidência, tamanho
aparente e perfis acústicos de forma determinística e educacional. Ele não é um
certificado de calibração de um HC-SR04 físico: temperatura, umidade, tolerância
do cristal, ruído elétrico, geometria exata do transdutor e variação entre lotes
não são modelados. Para metrologia comparável, execute também o protocolo de
5–50 cm descrito em [`laboratorio-hc-sr04.md`](laboratorio-hc-sr04.md).
