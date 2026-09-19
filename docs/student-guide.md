# Guia do aluno

## Primeiro circuito

No criativo, coloque em linha `Fonte 5 V -> Botao -> Resistor 220 ohms -> LED ->
GND`. Oriente o anodo do LED para a fonte e feche o botao. O LED deve acender e o
multimetro deve indicar aproximadamente `12,99 mA`.

Use a chave inglesa para corrigir orientacao sem quebrar blocos. No multimetro,
agachar + clique direito alterna tensao, corrente, resistencia e continuidade;
dois cliques em faces eletricas definem as pontas A e B.

## Diagnostico seguro

- `Circuito aberto`: confira botao, contatos e GND.
- `Polaridade incorreta`: inverta LED/diodo.
- `Sobrecorrente`: adicione resistencia antes de fechar o circuito.
- `Topologia nao suportada` ou `limite excedido`: reduza a rede; nenhum valor e inventado.

## RoboBoard

Coloque RoboPorts diretamente nas faces da placa e clique para selecionar
D0-D13, A0-A5, 5 V ou GND. Abra a RoboBoard e edite `Sketch.ino`. Atalhos: `Ctrl+S`
compila, `F5` inicia/para, `F6` alterna Editor/Serial, `F7` recarrega e `Ctrl+L`
limpa apenas a visualizacao Serial.

A API suportada e suas limitacoes estao em
[`firmware-compiler.md`](firmware-compiler.md). Comece pelos sketches em
[`examples/arduino`](../examples/arduino).

## Laboratorios

Siga os seis roteiros em [`curriculum/0.4-laboratorios.md`](curriculum/0.4-laboratorios.md).
Cada atividade aceita montagens eletricamente equivalentes; o servidor avalia o
resultado, nao uma sequencia fixa de coordenadas.
