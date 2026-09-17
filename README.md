# Craftônica: Robotics Lab 0.4.1

MVP educacional para Minecraft 1.7.10 e Forge 10.13.4.1614. O mundo funciona
como uma bancada: fonte, fios, botão, resistores, LED e GND são blocos reais. A
simulação elétrica é independente da Redstone e executada pelo servidor.

O plano pós-MVP, incluindo o contrato da futura placa Arduino-compatible, está
em [`ROADMAP.md`](ROADMAP.md).

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
`build/libs/craftonica-0.4.1.jar`.

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
- Potenciômetro de 10 kohms com terminais A, cursor e B.
- LED orientado, com queda de 2 V, brilho por corrente e falha persistente.
- Diodo DC linear por partes, com polaridade e parâmetros próprios.
- Chave de alavanca elétrica persistente, distinta do botão momentâneo.
- Multímetro nodal: mede a diferença de potencial entre duas faces e a corrente do
  ramo selecionado; medições ambíguas são rejeitadas.
- Chave inglesa, usada para girar componentes direcionais sem quebrá-los.
- Manual do Craftônica, com o primeiro circuito guiado dentro do jogo.

Todos aparecem na aba criativa `Craftônica` e possuem texturas autorais 16×16.
Ao passar o cursor sobre um componente, o inventário explica sua função, valor e
limites. Ao segurar um componente direcional, uma prévia translúcida mostra no
mundo a orientação resultante antes da colocação; os símbolos `+`, `-` e GND
mantêm os terminais distinguíveis sem depender somente de cor.

## Lições verificáveis

O motor de lições usa exclusivamente o snapshot elétrico publicado pelo servidor.
Ele valida componentes permitidos e grandezas com tolerâncias explícitas, sem
comparar coordenadas ou uma sequência fixa de blocos. Assim, montagens físicas
diferentes que produzam o mesmo comportamento elétrico podem concluir o desafio.

```text
/craftonica lesson list
/craftonica lesson start ohm-led-220
/craftonica lesson status
/craftonica lesson check <x> <y> <z>
```

As coordenadas devem apontar para qualquer bloco elétrico carregado da montagem,
a no máximo 64 blocos do jogador. O catálogo inclui circuito fechado, Lei de
Ohm com LED, polaridade, resistores em série e paralelo e diagnóstico de curto.
Falhas informam componente proibido, contagem incorreta, diagnóstico ausente,
rede pendente, erro do solver ou grandeza fora da tolerância.
O progresso é salvo por UUID no mundo, com formato NBT versionado, e sobrevive a
logout, morte e reinício do servidor.

O percurso completo de circuito fechado, Lei de Ohm, polaridade, série, paralelo
e diagnóstico está em
[`docs/curriculum/0.4-laboratorios.md`](docs/curriculum/0.4-laboratorios.md).

## Automação local via MCP

A integração de desenvolvimento é desativada por padrão. Para habilitá-la no
cliente, defina um segredo local de pelo menos 16 caracteres no ambiente do
launcher e adicione os argumentos JVM:

```text
- environment: CRAFTONICA_AUTOMATION_TOKEN=troque-por-um-segredo-local
-Dcraftonica.automation.enabled=true
-Dcraftonica.automation.port=8765
```

A bridge aceita conexões apenas em `127.0.0.1`, exige o segredo em todas as
requisições e executa ações do jogo na thread do cliente. Configure um cliente
MCP para iniciar o adaptador sem dependências externas:

```json
{
  "command": "python3",
  "args": ["/caminho/para/craftonica/tools/mcp/craftonica_mcp.py"],
  "env": {
    "CRAFTONICA_AUTOMATION_TOKEN": "troque-por-um-segredo-local",
    "CRAFTONICA_AUTOMATION_URL": "http://127.0.0.1:8765"
  }
}
```

O servidor MCP oferece `minecraft_state`, `minecraft_screenshot` e
`minecraft_action`. Ações disponíveis: chat/comando, olhar, selecionar a hotbar,
pressionar ou soltar teclas de movimento, usar, atacar, abrir o menu de pausa,
fechar a tela atual e
interagir com GUIs por ID de botão ou coordenada escalada. Quando a automação
opt-in está ativa, o cliente não pausa ao perder foco; isso permite controlar
menus no Niri/Wayland sem `xdotool`, XWayland ou captura do desktop. O estado
informa a classe e as dimensões da GUI e os IDs, textos e limites de seus
botões. A ação repete a classe observada para não operar uma tela que mudou.
Na seleção de mundos, também informa os saves disponíveis e aceita carregar um
save pelo índice retornado, sem simular mouse no compositor.

## Receitas e NEI

Todos os blocos e itens do Craftônica possuem receitas de bancada com materiais
vanilla. Fios usam redstone e linha; componentes estruturais usam ferro, pedra e
redstone; as faixas de cor dos resistores usam os corantes correspondentes; LED,
multímetro, chave inglesa e manual possuem receitas próprias.

As receitas são registradas como `IRecipe` padrão do Forge. O NEI 1.7.10 as
descobre automaticamente quando instalado, mas não é dependência do Craftônica:
o mod continua carregando e as receitas continuam funcionando sem CodeChicken.
O arquivo `tools/mcp/opencode.example.json` contém a entrada pronta para mesclar
na configuração do OpenCode. No Prism, configure o mesmo segredo em
`Settings > Environment variables` da instância e mantenha somente `enabled` e
`port` nos argumentos JVM, evitando expor o token na linha de comando.

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

O resultado esperado é fonte `5,00 V`, resistência externa `220,00 ohms`, corrente
aproximada `12,99 mA`, LED aceso e diagnóstico `Circuito fechado`. O modelo 0.3
inclui `10 ohms` internos na fonte e `1 ohm` dinâmico no LED. Abrir o botão
produz corrente zero. Se a medição ainda estiver sendo atualizada, aguarde um
tick e use o item novamente.

## Matriz manual

| Cenário | Procedimento | Resultado esperado |
| --- | --- | --- |
| Nominal | Montagem acima com 220 ohms | LED aceso e aproximadamente 12,99 mA |
| Circuito aberto | Abrir o botão | LED apagado, 0 mA e diagnóstico de circuito aberto |
| Polaridade | Recolocar o LED olhando para o sul | LED apagado, 0 mA e polaridade incorreta |
| Sobrecorrente | Remover o resistor e fechar o caminho | LED em brilho máximo e fumaça moderada |
| Queima | Manter a sobrecorrente por 20 ticks | LED apaga e passa a abrir o circuito |
| Persistência | Salvar e reabrir o mundo | LED queimado continua apagado |
| Paralelo | Criar dois ramos resistivos | Tensões nodais e correntes de cada ramo são resolvidas independentemente |
| Isolamento | Repetir a montagem em outro local | Alterar uma rede não muda a outra |
| Limite | Construir uma rede com mais de 1.024 blocos | Multímetro informa limite excedido sem travar |
| Idioma | Selecionar English (US) | Nomes e diagnósticos aparecem em inglês |
| Orientação | Segurar fonte, GND, botão, resistor ou LED | Prévia mostra posição e terminais sem alterar o mundo |

Para observar os 20 ticks com clareza, um segundo de jogo sem lag corresponde a
20 ticks. O contador é contínuo: abrir o circuito antes do último tick o zera.

## Arquitetura

- `electrical`: grafo, componentes, solver série, resultados e estado testável
  do LED; não depende de Forge para os cálculos.
- `network`: busca iterativa limitada, adaptação do mundo, fila de invalidação e
  cache servidor-autoritativo.
- `block`, `item` e `tile`: integração Forge, interação e persistência NBT.
- `registry` e `proxy`: registro comum de blocos, item, TileEntity e eventos.

O diodo usa um modelo linear por partes no pacote nodal puro: em condução,
`I = (Vd - Vf) / Rd`; em polarização reversa, a corrente é zero e o resultado
recebe diagnóstico de polaridade. A alavanca é uma chave estável, com metadata
sincronizado pelo servidor e estado `Closed` persistido em NBT. O botão existente
continua sendo uma chave independente normalmente aberta.

Colocar, remover ou acionar blocos apenas invalida o cache. A descoberta e a
solução ocorrem no fim do próximo tick. Redes sem alterações não são resolvidas
novamente. O cliente recebe somente brilho e estado queimado do LED; valores do
multímetro são consultados e enviados pelo servidor.

## Modelo suportado

O modelo 0.3 aceita redes DC resistivas em série e paralelo, múltiplas cargas,
fontes de Thévenin, LEDs, diodos, chaves, disjuntores e potenciômetros. A fonte
inclui resistência interna de `10 ohms`; o LED usa queda de `2 V` e resistência
dinâmica de `1 ohm`. Redes acima de 1.024 blocos ou 256 incógnitas MNA retornam
erro explícito.

O multímetro preserva o sinal da tensão conforme a ordem das pontas. Resistência
e continuidade usam uma análise auxiliar desenergizada com fonte-teste de `1 V`;
redes com LED ou diodo são recusadas explicitamente nesse modo. O relatório do
gate de precisão e desempenho está em
[`docs/reports/crl-26-simulation-gate-0.3.5.md`](docs/reports/crl-26-simulation-gate-0.3.5.md).

## Limitações conhecidas

- Não há CA, capacitores, indutores ou transistores.
- Não há RoboBoard, sensores, motores, programação ou mecânica.
- A fonte e o GND têm terminais apenas horizontais no fluxo normal de colocação.
- Curtos são limitados pela resistência interna simplificada da fonte; não há
  ainda um modelo não linear de limitação de corrente.
