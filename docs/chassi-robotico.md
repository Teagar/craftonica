# Chassi robótico móvel

O primeiro chassi móvel do Craftônica já existe como uma entidade persistente e
servidor-autoritativa. Nesta etapa ele permanece parado: ponte H, motores e tração
diferencial serão conectados nas etapas seguintes da missão.

Operadores podem criar temporariamente um chassi de validação com:

```text
/craftonica robot create
```

O comando só cria a entidade quando todo o volume de colisão está em chunks já
carregados, sem blocos, entidades ou líquidos. A montagem física substituirá esse
caminho temporário quando o sistema de conversão for implementado.

Para validar provisoriamente a mecânica antes da ligação do firmware, use perto de
um chassi de sua propriedade:

```text
/craftonica robot drive forward
/craftonica robot drive reverse
/craftonica robot drive left
/craftonica robot drive right
/craftonica robot drive stop
```

Esses comandos alimentam o mesmo modelo de ponte H que receberá GPIO e PWM na
próxima etapa. Eles não implementam navegação ou desvio de obstáculos ocultos.

Uma RoboBoard fixa já compilada pode fornecer firmware real ao chassi durante a
validação do host móvel:

```text
/craftonica robot firmware copy
/craftonica robot firmware start
/craftonica robot firmware status
/craftonica robot firmware stop
```

`copy` procura a RoboBoard acessível e o chassi do jogador em até 16 blocos. O
chassi recebe uma cópia limitada de firmware, checkpoint, sketch e histórico
Serial; a placa fixa não é alterada. O editor direto da entidade será conectado
junto da conversão física da montagem.

O host executa quadros de 800.000 ciclos AVR (50 ms) em worker isolado. Cada
quadro captura primeiro a pose confirmada e o cone do HC-SR04, executa o firmware
sem referências a `World` ou `Entity`, confirma checkpoint/identidade e só então
aplica GPIO/PWM à ponte H. O estado aplicado controla o quadro mecânico seguinte,
produzindo atraso fixo e determinístico de um quadro.

Pinagem de referência: ECHO D6, TRIG D7, esquerda D2/D4 com PWM D5 e direita
D8/D10 com PWM D9. Firmware parado, em falha ou com pinos sem `OUTPUT` zera o
esforço dos motores. O contador de amostras acústicas, o estado AVR e o histórico
Serial permanecem no NBT da entidade.

## Tração diferencial

Cada canal da ponte H trata direção, PWM de 0–255, zona morta abaixo de 32,
roda livre, frenagem, ausência de alimentação, ligação inválida e consumo
simplificado. As rodas são limitadas a 1,5 m/s, com aceleração de 3 m/s². Rodas no
mesmo sentido movem o chassi em linha reta; sentidos opostos giram no próprio eixo.

A entidade só integra física no servidor, em passos de 50 ms. Colisões usam o
Minecraft, terreno sem apoio e chunks descarregados causam parada segura, e o
chassi não tenta carregar terreno para continuar andando.

## Estado persistente

Cada entidade guarda:

- UUID próprio e UUID do proprietário;
- pose contínua e dimensões do chassi;
- manifesto canônico de até 32 módulos internos;
- fingerprint SHA-256 do manifesto;
- geração, quadro de simulação e estado operacional;
- indicação de retomada e diagnóstico limitado.

Schemas desconhecidos, fingerprints alterados, poses inválidas e manifestos
inconsistentes entram em `QUARANTINED`. Nesse estado o chassi fica inerte e não
solta componentes automaticamente.

Ao descarregar o chunk, o estado é suspenso, a geração é incrementada e nenhum
quadro de simulação avança. Depois da recarga há um tick de guarda antes de o
chassi voltar ao estado parado. Movimento não usa o relógio de parede.

## Multiplayer

O servidor registra e rastreia a entidade pelo canal padrão do Forge. Clientes
recebem pose, rotação e somente dois valores visuais limitados: estado operacional
e hash curto do manifesto. A pose é interpolada no cliente; proprietário,
manifesto completo e decisões futuras de física permanecem no servidor.

O modelo atual é um protótipo sem textura própria. Ele serve para validar escala,
orientação, persistência e tracking; o acabamento visual será feito no marco final.
