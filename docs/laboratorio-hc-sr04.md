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
Execute dez amostras por condição. A saída contém linhas CSV e um resumo com média
e desvio-padrão amostral. Na aba Serial, `Ctrl+C` copia o histórico sanitizado para
a área de transferência; cole-o numa planilha para calcular erro, regressão e R².

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

O ruído é pseudoaleatório e reprodutível a partir do mundo, posição e tempo. Os
parâmetros representam classes educacionais e não certificam um lote real de
HC-SR04. Para um trabalho metrológico sobre hardware físico, calibre cada unidade e
documente temperatura, umidade, instrumento de referência e incertezas.
