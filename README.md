# Craftônica: Robotics Lab 1.1.0

MVP educacional para Minecraft 1.7.10 e Forge 10.13.4.1614. O mundo funciona
como uma bancada: fonte, fios, botão, resistores, LED e GND são blocos reais. A
simulação elétrica é independente da Redstone e executada pelo servidor.

O histórico do plano do MVP até a placa Arduino-compatible está em
[`ROADMAP.md`](ROADMAP.md).

Para instalar e operar a versão estável, comece em
[`docs/installation-1.1.md`](docs/installation-1.1.md). Os roteiros de aluno,
professor, solução de problemas e release estão no [`docs/README.md`](docs/README.md).

Um operador pode gerar automaticamente uma sala superplana com seis projetos
Arduino clássicos usando `/craftonica showcase create`. Consulte
[`docs/showcase-map.md`](docs/showcase-map.md) antes de executar: a área delimitada
ao redor do jogador é substituída pela sala.

O comando `/craftonica uno create` gera uma placa Uno R3 funcional com D0-D13,
A0-A5, 5 V e GND vinculados à mesma RoboBoard. Consulte
[`docs/uno-r3-generator.md`](docs/uno-r3-generator.md).

A arquitetura normativa da RoboBoard e do pipeline de sketches está em
[`docs/rfc/0002-runtime-arduino-compatible-seguro.md`](docs/rfc/0002-runtime-arduino-compatible-seguro.md).

## Requisitos

- Linux x86_64 com `curl`, `sha256sum` e `tar`; ou JDK 8 instalado.
- `bubblewrap` e um gerenciador `systemd --user` para isolar compilação e runtime AVR.
- Minecraft 1.7.10 com Forge 10.13.4.1614 para instalar o JAR.

O projeto não suporta Java posterior ao 8 porque usa ForgeGradle 1.2.

## Build e testes

```sh
./scripts/gradle-java8.sh clean test build
```

O script baixa uma distribuição Temurin 8 fixada, verifica o SHA-256 e a guarda
em `~/.cache/craftonica`, sem alterar o Java padrão. O artefato instalável é
`build/libs/craftonica-1.1.0.jar`.

Para reconstruir e instalar com segurança na instância dedicada do Prism:

```sh
./scripts/install-prism.sh
```

Esse comando força um build limpo, verifica se o JAR foi reobfuscado para o
runtime Forge e só então substitui a versão anterior na pasta de mods. Isso é
importante porque uma tarefa `runClient` de desenvolvimento pode recriar o JAR
com nomes MCP, que não é instalável em launchers comuns. O instalador também
publica o worker AVR e seu launcher isolado em `minecraft/craftonica-runtime`.

### Migração e rollback de mundos

No primeiro carregamento de um mundo por esta geração do formato, o servidor
cria uma cópia verificada antes de iniciar os serviços do Craftônica. O backup
fica ao lado da pasta do mundo em
`.craftonica-backups/<mundo>/<id>/world`; `manifest.bin` contém tamanhos e
SHA-256 dos arquivos copiados. `session.lock` não é copiado. A publicação e o
marcador `craftonica/migration-state.bin` usam troca atômica no mesmo filesystem.

Essa etapa acontece durante o carregamento, antes do primeiro tick, e pode levar
tempo em mundos grandes. Se faltar espaço, houver link simbólico, alteração
concorrente ou não for possível publicar atomicamente, o carregamento é
interrompido sem avançar o marcador. Não execute duas instâncias sobre o mesmo
save.

Para rollback, feche completamente o Minecraft/servidor e preserve a pasta
migrada para diagnóstico. O backup é revalidado integralmente em toda abertura
do mundo; depois de uma abertura bem-sucedida com o JAR atual, restaure o
conteúdo da pasta `world` do backup em uma pasta de save vazia. Nunca abra o
diretório já migrado diretamente com uma versão antiga do mod.

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
- RoboBoard servidor-autoritativa, com firmware CRLFirmware verificado, checkpoint
  limitado e sincronização apenas do estado visual de execução, falha e D13.
- Editor industrial de `Sketch.ino` aberto com clique direito na RoboBoard, com
  compilação isolada, controle de execução e monitor Serial didático apenas TX.
- RoboPort físico, limitado a uma porta por face da RoboBoard, configurável como
  D0-D13, A0-A5, alimentação de 5 V ou GND.
- Sensores analógicos de luz e temperatura com saída determinística de 0-5 V.
- Buzzer de 220 ohms e motor CC de 100 ohms como cargas educacionais com
  indicação visual de atividade.

Todos aparecem na aba criativa `Craftônica` e possuem texturas autorais 16×16.
Ao passar o cursor sobre um componente, o inventário explica sua função, valor e
limites. Ao segurar um componente direcional, uma prévia translúcida mostra no
mundo a orientação resultante antes da colocação; os símbolos `+`, `-` e GND
mantêm os terminais distinguíveis sem depender somente de cor.

### RoboBoard e componentes educacionais

Coloque o RoboPort diretamente contra uma face da RoboBoard e clique nele para
escolher seu papel. A face oposta à placa é o único terminal que entra na rede.
Use o Configurador de RoboPort na placa e depois em cada terminal para vincular até
64 blocos de distância; clique comum apenas consulta o canal. A0-A5 mantêm os pinos AVR 14-19. Leituras
digitais usam LOW até 1,5 V, HIGH a partir de 3 V e conservam o último estado na
faixa indeterminada. Uma entrada ainda sem solução conserva o último estado
estável, inicialmente LOW, e fica marcada como indeterminada no diagnóstico da
placa; assim `setup()` ainda pode configurar saídas e pull-ups.

Os exemplos Arduino estão em [`examples/arduino`](examples/arduino). O PWM
reconhecido usa a média CC de `0..255`; buzzer e motor não simulam áudio ou
  mecânica. Servo, ponte H, `tone()` e `pulseIn()` permanecem fora desta versão.

### Editor de sketch e Serial

Clique com o botão direito na RoboBoard para abrir o único arquivo editável,
`Sketch.ino`. O editor não acessa o filesystem do cliente ou do servidor: o fonte
UTF-8 é enviado como dados limitados a 32 KiB, compilado no sandbox do servidor e
instalado somente após a verificação do firmware. O servidor continua autoritativo
sobre revisão, execução e falhas.

Atalhos: `Ctrl+S` compila, `F5` inicia ou para, `F6` alterna Editor/Serial,
`F7` recarrega o fonte instalado, `Ctrl+L` limpa somente a visualização Serial
local e `Esc` fecha. A aba Serial é
explicitamente apenas TX, sem entrada RX. Ela exibe bytes transmitidos de forma
segura, com offsets; o histórico do servidor é limitado aos 8 KiB mais recentes e
avisa quando dados antigos foram truncados.

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

### Modo professor

Operadores com permissão 2 podem criar atividades autoritativas no servidor. O
exemplo exige uma fonte, um GND, entre um e três resistores e um ou dois LEDs;
todos os LEDs devem conduzir `13 mA` com tolerância absoluta de `1 mA` e relativa
de `5%`:

```text
/craftonica teacher create lamp s:1:1,g:1:1,r:1:3,l:1:2 l:i:.013:.001:.05:all
/craftonica teacher assign teacher-lamp
/craftonica lesson start assigned
/craftonica teacher list
/craftonica teacher export
```

Definições e atribuição são persistidas no mundo. A exportação possui caminho
fixo `craftonica/exports/lesson-completions.csv` dentro do save e contém somente
`lesson_id` e total de conclusões. UUID, nome, posição e medição individual não
são exportados por padrão.

A forma compacta respeita o limite de 100 caracteres do chat legado. Aliases de
componentes: `w` fio, `s` fonte, `g` GND, `k` chave, `b` disjuntor, `r` resistor,
`p` potenciômetro, `l` LED e `d` diodo. Grandezas: `i` corrente, `v` tensão e `p`
potência; quantificadores: `a` qualquer ramo ou `all` todos os ramos.

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

LEDs existem nas 16 cores de lã. A cor fica opaca quando desligada e saturada
quando ligada, sem alterar polaridade, corrente ou persistência de sobrecorrente.

Use qualquer um dos 16 corantes vanilla com clique direito para mudar a cor de
um fio. A cor é preservada ao quebrar e recolocar o bloco e serve apenas para
organização visual: fios de cores diferentes continuam eletricamente conectados.
Um fio padrão herda automaticamente a cor quando todos os fios vizinhos possuem
a mesma cor; diante de cores conflitantes, ele mantém a cor do item.
Use o Roteador de fios para bloquear uma face específica quando cabos paralelos
precisarem permanecer em redes elétricas separadas; a máscara fica salva no mundo.

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
- A RoboBoard não simula áudio, mecânica, servos ou ponte H; essas integrações
  estão fora do escopo da versão 1.0.
- A fonte e o GND têm terminais apenas horizontais no fluxo normal de colocação.
- Curtos são limitados pela resistência interna simplificada da fonte; não há
  ainda um modelo não linear de limitação de corrente.
