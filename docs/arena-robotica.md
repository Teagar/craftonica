# Arena de validação do robô móvel

Operadores podem gerar a arena com:

```text
/craftonica arena create
```

O volume de 51 × 51 blocos é centralizado na posição atual e usa o nível dos pés
como piso de circulação. Todos os chunks do volume precisam estar carregados; o
comando recusa a operação antes de alterar qualquer bloco caso encontre chunk
ausente, altura inválida ou uma entidade na posição de uma futura parede/alvo.
Na primeira criação, a origem é persistida por dimensão. Chamadas posteriores e
reinícios reutilizam essa origem em vez de calcular outra a partir da posição para
a qual o jogador foi teleportado.

## Planta reproduzível

A planta lógica tem 17 × 17 células, ampliadas em 3 × 3 blocos. Portanto, cada
corredor possui pelo menos três blocos de largura para o chassi de 1,8 bloco. Um
algoritmo determinístico cria sempre o mesmo labirinto conectado, incluindo:

- curvas e aproximações oblíquas;
- becos sem saída;
- área inicial marcada em verde;
- área de estacionamento/recuperação marcada em amarelo;
- quatro alvos pequenos, alternando MDF e espuma, somente em becos;
- paredes visíveis de madeira/MDF, vidro/plástico rígido e lã absorvente;
- perímetro de quartzo com três blocos de altura.

As regiões rígidas tendem a produzir eco mais estável. A lã e os alvos de espuma
usam perfis absorventes, com maior viés, dispersão e perda. Os alvos pequenos têm
cobertura aparente menor no cone de 13 raios. Não existem colisões ou barreiras
invisíveis: toda geometria acústica também é geometria de blocos.

## Idempotência e recuperação

O gerador escreve somente `x=0..50`, `z=0..50` e `y=-1..6` relativos à origem.
Cada voxel desse volume recebe um estado explícito, inclusive ar; executar o
comando novamente na mesma posição produz exatamente a mesma planta, sem empilhar
paredes ou deixar resíduos. Blocos fora do volume não são consultados nem alterados.

Robôs que já estejam em corredores permanecem no mundo durante uma regeneração.
Se a caixa de colisão de qualquer entidade cruzaria uma futura parede ou alvo, o
comando é recusado. Para recuperação, pare o firmware, leve ou recrie o robô na
área amarela e use a chave inglesa conforme o roteiro de desmontagem.

A arena é formada por blocos persistentes normais e o robô conserva seu manifesto,
pose, checkpoint AVR e Serial no NBT da entidade. Salvar e reiniciar no meio do
ensaio não depende de memória transitória do gerador.

## Roteiro manual

1. Gere a arena e anote a origem informada no chat.
2. Gere novamente sem mover o jogador e confirme que a planta não muda.
3. Monte ou posicione o robô no quadrado verde, apontado para o primeiro corredor.
4. Compare a mesma estratégia nas regiões de madeira, vidro e lã.
5. Observe timeouts diante dos alvos pequenos/absorventes sem tratá-los como zero.
6. Estacione na área amarela, pare o firmware e valide a desmontagem.
7. Reinicie o mundo durante um ensaio e confira pose, firmware e geometria.

## Sketch autônomo de um HC-SR04

Com uma RoboBoard vazia a até 16 blocos, execute:

```text
/craftonica robot sketch autonomous
```

Abra a placa, compile com `Ctrl+S`, aproxime-se do robô montado e execute
`/craftonica robot firmware copy` seguido de `/craftonica robot firmware start`.
O fonte também está em
`examples/arduino/hc_sr04_autonomous_robot/hc_sr04_autonomous_robot.ino`.

O controlador usa D7/D6 para TRIG/ECHO, D2/D4/D5 para o lado esquerdo e
D8/D10/D9 para o lado direito. Cada decisão exige ao menos dois ecos entre três
tentativas e aplica mediana. Diante de obstáculo, ele freia, recua e usa giros do
próprio chassi para medir direita e esquerda. Se a frente perder eco, ele freia e
faz duas varreduras; só realiza um avanço lento quando outra direção confirmou que
o sensor ainda responde. Se todas as varreduras expirarem, mantém a ponte H em
freio e registra `TIMEOUT_ALL_STOP,NA` na Serial.

A arena marca em laranja a abertura externa e o início da baia final. No modelo
lógico de seed fixa, a política frente/direita/esquerda chega a essa saída em no
máximo 1.024 transições de célula. No mundo físico, a tolerância de giro depende
de carga, aceleração e colisão; o ensaio final deve registrar tempo, colisões,
timeouts e sucesso separadamente para paredes rígidas e absorventes.
