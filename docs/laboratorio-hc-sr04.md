# Laboratório de caracterização do HC-SR04

Operadores podem criar uma bancada completa com quatro estações usando
`/craftonica sonar create`. Cada RoboBoard recebe o sketch do material correspondente;
basta abrir a placa, compilar com `Ctrl+S`, executar com `F5` e abrir o Serial com `F6`.

O laboratório permite estudar o sensor, em vez de retornar uma distância ideal. O
resultado depende da distância, da incidência e do perfil acústico do obstáculo.
MDF e plástico rígido tendem a produzir ecos repetíveis; isopor e espuma adicionam
viés, dispersão e, em condições desfavoráveis, perda de eco.

## Ligações

O HC-SR04 é orientável. Olhando para a face com os dois transdutores:

- face superior: VCC, ligado ao RoboPort `5V`;
- face inferior: GND, ligado ao RoboPort `GND`;
- face esquerda: TRIG, ligado a um RoboPort digital de saída;
- face direita: ECHO, ligado a outro RoboPort digital de entrada.

O exemplo [`hc_sr04_metrology.ino`](../examples/arduino/hc_sr04_metrology/hc_sr04_metrology.ino)
usa D7 para TRIG e D6 para ECHO. O runtime reconhece a ligação física pelos nós do
circuito; os números não são fixos no sensor.

Uma RoboBoard aceita, nesta versão, no máximo um HC-SR04 completo. Ligações
duplicadas ou mais de um sensor deixam o periférico indisponível em vez de escolher
um resultado arbitrário.

## Modo de calibração: 5–50 cm

Coloque um trilho acústico diretamente diante dos transdutores. Há quatro corpos de
prova: MDF, plástico rígido, isopor e espuma.

- clique: avança a distância nominal em 5 cm, retornando a 5 cm depois de 50 cm;
- agache e clique: avança a incidência em 15°, retornando a 0° depois de 60°.

Altere `MATERIAL` e `NOMINAL_CM` no sketch para identificar a condição ensaiada.
Cada execução produz exatamente dez amostras e para, evitando que um lote sobrescreva
o histórico limitado. Depois de cada distância, execute `/craftonica sonar export`.
O comando incorpora os históricos das RoboBoards do jogador em até 64 blocos, usando
`material + nominal + amostra` como chave: repetir uma condição substitui o lote, não
o duplica. Os arquivos ficam em `craftonica/exports` dentro do save:

- `hc-sr04-samples.csv`: dados brutos, incluindo `NA,0` para timeout;
- `hc-sr04-metrics.csv`: total, válidos, timeouts, média, erro absoluto médio e
  desvio-padrão por distância; inclinação, intercepto e R² da regressão
  `medida = inclinação × nominal + intercepto` por material.

O exportador só declara o protocolo completo com 400 chaves distintas. Histórico
Serial truncado ou CSV inválido é recusado, sem modificar o arquivo já confirmado.
`Ctrl+C` continua disponível para inspeção e análise externa.

## Modo robô

Sem um trilho adjacente, o sensor emite um cone de 15° com 13 raios determinísticos
e procura superfícies entre 2 e 400 cm. Sua pose aceita yaw e pitch contínuos, sem
ficar restrita às quatro direções dos blocos; o bloco fixo apenas adapta sua face à
mesma consulta que será usada pelo robô móvel. Um bloco do Minecraft corresponde a
100 cm neste modo.

Blocos e entidades com colisão podem refletir o eco. Os raios que atingem a mesma
superfície são agrupados para estimar quanto do cone o alvo ocupa. Alvos menores,
inclinados ou distantes têm menor confiança e maior chance de timeout. Madeira,
vidro, lã, esponja e outros blocos continuam classificados em perfis acústicos;
entidades vivas usam um perfil absorvente. A consulta considera somente chunks já
carregados e nunca gera terreno para procurar um obstáculo.

## Protocolo sugerido

1. Use a mesma unidade do sensor e mantenha sua posição fixa.
2. Para cada material, ensaie de 5 a 50 cm em passos de 5 cm.
3. Colete dez leituras com intervalo de 100 ms por marca.
4. Preserve timeouts como dados ausentes; não os transforme em zero centímetros.
5. Calcule média, erro absoluto, desvio-padrão amostral, reta de calibração e R².
6. Compare superfícies rígidas e porosas e repita com incidências diferentes.

## Filtros para o robô

Os exemplos `hc_sr04_moving_average`, `hc_sr04_median` e
`hc_sr04_timeout_rejection` usam os mesmos pinos D7/D6 e compilam no runtime AVR.
Média móvel reduz ruído aproximadamente gaussiano; mediana rejeita leituras válidas
isoladas; a versão de timeout demonstra uma API booleana para a lógica de navegação.
Nos três casos, uma leitura sem eco continua sendo impressa como `NA` e a saída
filtrada daquele ciclo também é `NA`: nenhum filtro converte timeout em zero, repete
uma distância antiga ou inventa eco.

O ruído é pseudoaleatório e reprodutível a partir do mundo, posição e tempo. Os
parâmetros representam classes educacionais e não certificam um lote real de
HC-SR04. Para um trabalho metrológico sobre hardware físico, calibre cada unidade e
documente temperatura, umidade, instrumento de referência e incertezas.
