# Craftônica: Robotics Lab

MVP educacional para Minecraft 1.7.10 e Forge 10.13.4.1614. O mundo funciona
como uma bancada: fonte, fios, botão, resistores, LED e GND são blocos reais. A
simulação elétrica é independente da Redstone e executada pelo servidor.

## Requisitos

- Linux x86_64 com `curl`, `sha256sum` e `tar`; ou JDK 8 instalado.
- Minecraft 1.7.10 com Forge 10.13.4.1614 para instalar o JAR.

O projeto não suporta Java posterior ao 8 porque usa ForgeGradle 1.2.

## Build e testes

```sh
./scripts/gradle-java8.sh clean test build
```

O script baixa uma distribuição Temurin 8 fixada, verifica o SHA-256 e a guarda
em `~/.cache/craftonica`, sem alterar o Java padrão. O artefato instalável é
`build/libs/craftonica-0.1.3.jar`.

Para reconstruir e instalar com segurança na instância dedicada do Prism:

```sh
./scripts/install-prism.sh
```

Esse comando força um build limpo, verifica se o JAR foi reobfuscado para o
runtime Forge e só então substitui a versão anterior na pasta de mods. Isso é
importante porque uma tarefa `runClient` de desenvolvimento pode recriar o JAR
com nomes MCP, que não é instalável em launchers comuns.

Para desenvolvimento:

```sh
./scripts/gradle-java8.sh runClient
./scripts/gradle-java8.sh runServer
```

O primeiro início do servidor cria `run/eula.txt`; aceite a EULA antes de
reiniciar. Se o serviço legado de assets estiver indisponível, use:

```sh
./scripts/gradle-java8.sh runServer -PskipAssets
./scripts/gradle-java8.sh runClient -PskipAssets -PcraftonicaAssetDir=/caminho/para/assets
```

## Componentes

- Fio elétrico, com conexão nas seis faces.
- Fonte CC de 5 V e GND, cada um com um terminal orientado.
- Botão elétrico normalmente aberto, alternado com clique direito.
- Resistores de 220 ohms, 1 kohm e 10 kohms.
- LED orientado, com queda de 2 V, brilho por corrente e falha persistente.
- Multímetro, usado com clique direito sobre um fio ou componente.

Todos aparecem na aba criativa `Craftônica` e possuem texturas autorais 16×16.

Os componentes usam modelos próprios em vez de cubos pintados: resistores têm
corpo axial e terminais, o botão possui base e atuador móvel, e o LED possui
base, bulbo e duas pernas. O terminal vermelho indica positivo/ânodo; o terminal
azul-ciano indica GND/cátodo. O fio só desenha um braço quando a face vizinha é
um terminal elétrico válido, evitando conexões visuais falsas.

Use qualquer um dos 16 corantes vanilla com clique direito para mudar a cor de
um fio. A cor é preservada ao quebrar e recolocar o bloco e serve apenas para
organização visual: fios de cores diferentes continuam eletricamente conectados.

## Texturas

As texturas seguem o guia público de estilo Minecraft do Blockbench: resolução
16×16, paleta curta, pixels deliberados sem antialiasing e iluminação partindo
do canto superior esquerdo. O pipeline usa ImageMagick e pode ser reproduzido:

```sh
./scripts/generate-textures.sh
```

Para edição manual, Blockbench oferece preview 3D; Piskel é uma opção gratuita
e open source no navegador; Aseprite oferece edição e automação por CLI, mas é
comercial. O projeto não depende de geração por IA ou de uma API remota.

## Laboratório reproduzível

Crie um mundo criativo plano e monte uma linha no eixo norte-sul, com blocos
adjacentes nesta ordem:

```text
norte
[Fonte 5 V] [Botão] [Resistor 220 ohms] [LED] [GND]
sul
```

1. Coloque a fonte olhando para o sul, para que seu terminal aponte ao botão.
2. Coloque botão e resistor olhando para norte ou sul; seus terminais ficam no
   mesmo eixo.
3. Coloque o LED olhando para o norte. O lado voltado à fonte é o ânodo.
4. Coloque o GND olhando para o norte, com o terminal voltado ao LED.
5. Clique no botão para fechar o circuito.
6. Use o multímetro no resistor, LED ou em um fio intercalado.

O resultado esperado é fonte `5,00 V`, resistência `220,00 ohms`, corrente
aproximada `13,64 mA`, LED aceso e diagnóstico `Circuito fechado`. Abrir o botão
produz corrente zero. Se a medição ainda estiver sendo atualizada, aguarde um
tick e use o item novamente.

## Matriz manual

| Cenário | Procedimento | Resultado esperado |
| --- | --- | --- |
| Nominal | Montagem acima com 220 ohms | LED aceso, brilho 11/15 e 13,64 mA |
| Circuito aberto | Abrir o botão | LED apagado, 0 mA e diagnóstico de circuito aberto |
| Polaridade | Recolocar o LED olhando para o sul | LED apagado, 0 mA e polaridade incorreta |
| Sobrecorrente | Remover o resistor e fechar o caminho | LED em brilho máximo e fumaça moderada |
| Queima | Manter a sobrecorrente por 20 ticks | LED apaga e passa a abrir o circuito |
| Persistência | Salvar e reabrir o mundo | LED queimado continua apagado |
| Topologia inválida | Criar uma derivação de fio ou segunda fonte | Multímetro informa circuito não suportado |
| Isolamento | Repetir a montagem em outro local | Alterar uma rede não muda a outra |
| Limite | Construir uma rede com mais de 1.024 blocos | Multímetro informa limite excedido sem travar |
| Idioma | Selecionar English (US) | Nomes e diagnósticos aparecem em inglês |

Para observar os 20 ticks com clareza, um segundo de jogo sem lag corresponde a
20 ticks. O contador é contínuo: abrir o circuito antes do último tick o zera.

## Arquitetura

- `electrical`: grafo, componentes, solver série, resultados e estado testável
  do LED; não depende de Forge para os cálculos.
- `network`: busca iterativa limitada, adaptação do mundo, fila de invalidação e
  cache servidor-autoritativo.
- `block`, `item` e `tile`: integração Forge, interação e persistência NBT.
- `registry` e `proxy`: registro comum de blocos, item, TileEntity e eventos.

Colocar, remover ou acionar blocos apenas invalida o cache. A descoberta e a
solução ocorrem no fim do próximo tick. Redes sem alterações não são resolvidas
novamente. O cliente recebe somente brilho e estado queimado do LED; valores do
multímetro são consultados e enviados pelo servidor.

## Modelo suportado

O MVP aceita um único caminho DC em série com exatamente uma fonte, um GND e um
LED, além de botão, fios e um ou mais resistores. A corrente usa
`I = (5 V - 2 V) / Rtotal`. Redes ramificadas, múltiplas fontes/GND, ciclos e
mais de 1.024 blocos retornam erro explícito.

## Limitações conhecidas

- Não há circuitos em paralelo, CA, capacitores, indutores ou transistores.
- Não há RoboBoard, sensores, motores, programação ou mecânica.
- Não há chave inglesa; para mudar orientação, quebre e recoloque o componente.
- A fonte e o GND têm terminais apenas horizontais no fluxo normal de colocação.
- O modelo sem resistor reporta corrente ideal sem limite; não inventa um valor
  numérico de curto-circuito.
