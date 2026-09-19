# Mapa laboratório Arduino

## Gerar automaticamente

Entre como operador em um mundo superplano, fique longe de construções que devem
ser preservadas e execute:

```text
/craftonica showcase create
```

O comando substitui uma área fixa de `49 x 39 x 10` blocos próxima ao jogador,
constrói a sala e move o jogador para a entrada. Não requer WorldEdit nem outro
mod. Executá-lo novamente na mesma região restaura o layout original das
estações, apagando alterações feitas dentro da área.

## Estações

1. Blink: LED no D13, alternando a cada 500 ms.
2. Fade PWM: brilho do LED no D9 por `analogWrite`.
3. Semáforo: sequência clássica nos pinos D10, D11 e D12.
4. Monitor de luz: leitura A0 enviada ao Serial.
5. Potenciômetro: leitura A1, Serial e saída PWM D9.
6. Alarme de temperatura: leitura A2 e decisão digital no D8.

Cada bancada possui RoboBoard com sketch pré-carregado, circuito funcional,
placa, baú com livro de quatro páginas, multímetro, chave inglesa, manual e peças
de reposição. Abra a placa, use `Ctrl+S` para compilar e `F5` para iniciar. Nos
projetos de entrada, o sensor ou cursor do potenciômetro já está conectado ao pino
analógico indicado.

O buzzer e o motor permanecem cargas visuais, sem áudio ou mecânica simulados. O
semáforo possui três ramos funcionais: D10 vermelho, D11 amarelo e D12 verde.
