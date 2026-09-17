# Roadmap do Craftônica

Este documento define a evolução do MVP elétrico até um laboratório educacional
programável. A ordem é deliberada: primeiro a montagem precisa ser clara, depois
o simulador precisa ganhar profundidade e somente então entram programação e
robótica.

## Princípios

- O mundo é a bancada. Componentes continuam sendo blocos, não uma GUI de
  montagem separada.
- O servidor é autoritativo para circuitos, programas e estados persistentes.
- Nenhuma limitação pode ser escondida por física inventada.
- Interfaces visuais não podem depender somente de cor.
- Mundos existentes devem continuar carregando entre versões.
- Cada marco deve compilar com Java 8, abrir no Forge 1.7.10 e ser validado em
  cliente Prism e servidor dedicado.

## 0.2 - Construção e usabilidade

Objetivo: permitir que um aluno monte e corrija circuitos sem lutar contra a
interface.

Entregas:

- chave inglesa para girar componentes sem quebrá-los;
- indicação de terminais, polaridade e orientação durante a colocação;
- tooltips com função, valor e limites de cada componente;
- manual dentro do jogo com o primeiro circuito guiado;
- multímetro com duas pontas e modos de tensão, corrente, resistência e
  continuidade;
- receitas e integração com NEI para descoberta no modo sobrevivência;
- sons e partículas moderados para conexão, chaveamento e falhas.

Nesta fase, as duas pontas confirmam que os blocos pertencem à mesma rede e os
modos exibem as grandezas globais do modelo em série. Diferenças de potencial
entre pontos e correntes por ramo dependem do solver por nós da versão 0.3; o
jogo deve declarar esse limite em vez de inventar valores locais.

Critérios de saída:

- todos os componentes podem ser orientados e diagnosticados sem serem
  destruídos;
- o circuito fonte-botão-220 ohms-LED-GND pode ser construído seguindo apenas o
  material apresentado dentro do jogo;
- polaridade permanece compreensível em escala de cinza;
- nenhuma ferramenta altera redes no cliente sem validação do servidor.

## 0.3 - Simulação elétrica

Objetivo: substituir o solver de caminho único por um modelo DC resistivo com
tensões por nó.

Entregas:

- grafo explícito de nós e ramos;
- circuitos série-paralelo e múltiplas cargas;
- tensão em cada nó e corrente em cada ramo;
- múltiplos LEDs e resistores na mesma rede;
- fontes com resistência interna e curto-circuito limitado;
- fusível ou disjuntor;
- diodo, potenciômetro e chave de alavanca;
- diagnósticos para nó flutuante, curto, polaridade e potência excedida.

O primeiro solver dessa fase continua limitado a DC em regime permanente. CA,
indutores e capacitores exigem um solver temporal separado e não entram por
atalho.

Critérios de saída:

- resultados conferem com casos de referência calculados por análise nodal;
- redes diferentes e chunks descarregados permanecem isolados;
- custo de atualização é limitado e medido em redes de 1.024 blocos;
- multímetro mede grandezas locais, não apenas um resultado global.

## 0.4 - Ensino

Objetivo: transformar componentes corretos em uma experiência de aprendizagem.

Entregas:

- sequência de lições sobre circuito fechado, Lei de Ohm, polaridade, série,
  paralelo e diagnóstico;
- desafios com condições verificadas pelo servidor;
- explicações acionáveis para erros, com destaque do ponto problemático;
- mundos de laboratório reproduzíveis;
- modo professor para definir objetivo, componentes permitidos e tolerâncias;
- registro exportável de conclusão, sem coletar dados pessoais por padrão.

Critérios de saída:

- cada conceito possui explicação, montagem, medição e desafio;
- soluções alternativas eletricamente corretas são aceitas;
- o sistema explica por que uma montagem falhou sem entregar silenciosamente a
  resposta.

## 0.5 - Robótica e placa Arduino-compatible

Objetivo: controlar sensores e atuadores no mundo com sketches Arduino reais,
sem substituir a linguagem por uma DSL proprietária.

### Contrato de linguagem

O alvo inicial será compatibilidade de código-fonte com Arduino Uno R3 e Arduino
AVR Core 1.8.6. O usuário escreverá sketches `.ino` em C++, incluindo:

```cpp
void setup() {
  pinMode(13, OUTPUT);
}

void loop() {
  digitalWrite(13, HIGH);
  delay(500);
  digitalWrite(13, LOW);
  delay(500);
}
```

O perfil inicial deve preservar:

- `setup()` e `loop()`;
- tipos, operadores, funções, classes e constantes da linguagem C++ suportada
  pela toolchain AVR escolhida;
- `pinMode`, `digitalRead`, `digitalWrite`, `analogRead`, `analogWrite`,
  `delay`, `delayMicroseconds`, `millis`, `micros` e `Serial`;
- constantes como `HIGH`, `LOW`, `INPUT`, `OUTPUT` e `INPUT_PULLUP`;
- mensagens de compilação com arquivo e linha do sketch.

Não será criada uma linguagem visual ou textual diferente com nomes parecidos.
Bibliotecas adicionais só serão anunciadas como compatíveis quando seus testes
passarem; código dependente de hardware AVR não emulado deve produzir erro
explícito.

### Toolchain e execução

- compilar sketches com frontend C++ e Arduino AVR Core reais, em processo
  separado do Minecraft;
- restringir includes a um diretório de SDK somente leitura e nunca expor o
  filesystem do servidor ao sketch;
- impor timeout, memória, tamanho de firmware e taxa de compilação;
- executar o firmware em emulador AVR isolado ou backend equivalente validado
  contra sketches de referência;
- converter pinos em eventos de entrada e saída por uma ponte servidor-side;
- avançar tempo virtual com orçamento por tick; `delay()` suspende a placa, não
  a thread do servidor;
- persistir sketch, hash da toolchain, firmware e estado necessário em formato
  versionado;
- nunca carregar bytecode ou bibliotecas nativas fornecidas pelo jogador dentro
  da JVM do jogo.

### Hardware virtual

- placa com pinos digitais, analógicos, 5 V e GND claramente modelados;
- LED integrado no pino 13;
- botão, sensor de luz, sensor de distância e sensor de temperatura;
- buzzer, servo, motor DC e ponte H;
- PWM e leitura analógica com resolução documentada;
- monitor serial com limites de tamanho e frequência.

Critérios de saída:

- sketches Arduino de referência compilam sem tradução manual;
- Blink controla o LED integrado com temporização determinística;
- exemplos de entrada digital, leitura analógica, PWM, servo e Serial têm testes
  automatizados;
- um loop infinito ou sketch malicioso não bloqueia nem acessa o processo do
  servidor;
- diferenças em relação ao Uno/Core alvo aparecem em uma matriz pública de
  compatibilidade.

## 1.0 - Produto estável

Objetivo: tornar o laboratório seguro para mundos duradouros, turmas e
multiplayer.

Entregas:

- servidor dedicado e multiplayer testados;
- migração de dados entre todas as versões suportadas;
- configurações de dificuldade, realismo e limites;
- acessibilidade para daltonismo, escala de interface e redução de partículas;
- documentação completa em português e inglês;
- perfil de desempenho, telemetria local opcional e diagnóstico administrativo;
- empacotamento reproduzível de mod, toolchain e dependências licenciadas.

Critérios de saída:

- nenhum bug conhecido de corrupção de mundo ou autoridade cliente;
- matriz de compatibilidade e limitações publicada;
- laboratórios de longa duração sobrevivem a reinícios, unload de chunks e
  entrada e saída de jogadores;
- todas as dependências e marcas de terceiros são atribuídas corretamente.

## Ordem imediata

1. Chave inglesa e rotação segura.
2. Tooltips e indicação de terminais durante a colocação.
3. Manual e primeiro laboratório guiado.
4. Multímetro com duas pontas.
5. Projeto do solver nodal da versão 0.3.
