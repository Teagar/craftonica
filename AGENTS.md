# Workspace Instructions

## Project
Craftonica

## Objective
Você é um desenvolvedor especialista em Java, Minecraft Forge 1.7.10 e simulação de circuitos.

Quero que você desenvolva um MVP funcional de um mod educacional chamado “Craftônica: Robotics Lab”.

Informações do projeto:

* Minecraft Java: 1.7.10
* Forge: 10.13.4.1614
* Java: 8
* Mod ID: craftonica
* Package base: br.com.craftonica
* Idioma principal: português brasileiro
* Objetivo: ensinar eletrônica e robótica por meio de circuitos construídos com blocos no mundo.

Conceito principal:

Não quero uma interface 2D semelhante ao Tinkercad. Os componentes devem ser blocos colocados diretamente no mundo, como acontece com a Redstone.

O próprio mundo do Minecraft funcionará como uma grande protoboard. O aluno deverá montar fisicamente circuitos como:

[Fonte 5 V] → [Botão] → [Resistor 220 Ω] → [LED] → [GND]

O sistema elétrico deve ser separado completamente do sistema de Redstone do Minecraft. Não represente tensão usando apenas o nível de potência da Redstone.

Escopo do MVP:

Implemente os seguintes blocos:

1. Fio elétrico

   * Conecta-se visualmente nas seis direções.
   * Detecta componentes elétricos vizinhos.
   * Forma uma rede elétrica.
   * Redes diferentes não devem interferir umas nas outras.
   * Utilize uma textura provisória se não houver textura definitiva.

2. Fonte de 5 V

   * Fornece tensão contínua de 5 volts.
   * Possui terminal positivo.
   * Deve participar de apenas um circuito por vez no MVP.

3. GND

   * Representa o retorno do circuito.
   * Um circuito só funciona se existir caminho fechado entre a fonte e o GND.

4. Botão elétrico

   * É diferente do botão padrão do Minecraft.
   * Funciona como chave normalmente aberta.
   * Alterna entre circuito aberto e fechado quando pressionado.
   * Possui dois terminais em faces opostas.

5. Resistores

   * 220 Ω.
   * 1 kΩ.
   * 10 kΩ.
   * Cada resistor deve ter identificação visual própria.
   * Possui dois terminais em faces opostas.
   * Sua orientação depende da direção em que foi colocado.

6. LED

   * Possui ânodo e cátodo.
   * Possui tensão direta simplificada de 2 V.
   * Só acende com a polaridade correta.
   * O brilho depende aproximadamente da corrente.
   * Deve emitir partículas de fumaça em situação de sobrecorrente.
   * Corrente recomendada: até 20 mA.
   * Se permanecer acima de 30 mA durante 20 ticks, deve ficar queimado.
   * O estado queimado deve ser salvo no mundo.
   * Um LED queimado não deve acender novamente.

7. Item multímetro

   * Ao clicar com o botão direito em um fio ou componente, mostra no chat:

     * tensão da fonte;
     * corrente estimada;
     * resistência equivalente;
     * circuito aberto ou fechado;
     * polaridade incorreta;
     * sobrecorrente;
     * circuito não suportado.
   * As mensagens devem estar em português brasileiro.

Interação no mundo:

* Não crie tela de montagem.
* Não crie protoboard em GUI.
* Os circuitos devem existir como blocos reais.
* Componentes direcionais devem girar conforme a posição do jogador.
* Uma chave inglesa poderá ser adicionada para girar componentes sem quebrá-los.
* Os fios devem mudar visualmente de acordo com suas conexões.
* Use partículas para indicar problemas, mas evite partículas excessivas.

Modelo elétrico do MVP:

Implemente somente circuitos resistivos DC simples contendo:

* exatamente uma fonte de 5 V;
* exatamente um GND;
* fios ideais;
* um botão;
* resistores em série;
* um LED em série;
* um único caminho fechado.

Use a Lei de Ohm:

I = (Vfonte - Vled) / Rtotal

Exemplo:

* Fonte: 5 V
* LED: queda de 2 V
* Resistor: 220 Ω
* Corrente aproximada: 13,64 mA

Regras importantes:

* Um resistor não reduz tensão sozinho como se fosse Redstone.
* Não invente uma física eletrônica incorreta.
* Se houver ramificações, fontes múltiplas, curto complexo ou topologia ainda não suportada, marque o circuito como “não suportado”.
* Se não houver resistor com um LED, considere uma sobrecorrente.
* Se o LED estiver invertido, a corrente deve ser zero.
* Se o botão estiver aberto, a corrente deve ser zero.
* Não implemente circuitos em paralelo, capacitores, indutores, transistores ou corrente alternada nesta versão.

Arquitetura sugerida:

* ElectricalComponent: interface para componentes.
* ElectricalTerminal: representação de um terminal.
* ElectricalNetworkManager: encontra e atualiza redes.
* CircuitGraph: representa os blocos e conexões.
* SimpleCircuitSolver: valida e calcula circuitos em série.
* CircuitResult: contém tensão, corrente, resistência e possíveis erros.
* BlockElectricalWire.
* BlockPowerSource.
* BlockGround.
* BlockElectricalButton.
* BlockResistor.
* BlockLED.
* ItemMultimeter.

Você pode ajustar os nomes se houver uma estrutura mais apropriada para Forge 1.7.10, mas mantenha responsabilidades separadas.

Atualização das redes:

* Quando um componente for colocado, removido, girado ou ativado, marque a rede como desatualizada.
* Recalcule a rede no próximo tick do servidor.
* Não faça atualização recursiva diretamente dentro de eventos de vizinhança.
* Use busca em largura ou profundidade para encontrar componentes conectados.
* Limite cada rede a no máximo 1.024 blocos.
* Interrompa a busca e reporte erro se esse limite for ultrapassado.
* Evite recalcular uma rede que não sofreu alterações.
* Toda a simulação deve ser autoritativa no servidor.
* O cliente deve receber somente os estados necessários para renderização.

Persistência e segurança:

* Salve estados persistentes usando NBT.
* Salve principalmente o estado queimado do LED e configurações dos componentes.
* Valide todos os dados recebidos do cliente.
* Não execute loops ilimitados.
* Não bloqueie a thread principal.
* Não confie em informações calculadas pelo cliente.

Organização:

* Separe código comum, cliente, blocos, itens, rede elétrica e simulação.
* Crie arquivos de idioma para pt_BR e en_US.
* Use nomes técnicos em inglês no código.
* Use português apenas nos textos apresentados ao jogador.
* Adicione comentários somente onde eles realmente explicarem uma decisão importante.
* Não misture APIs de versões modernas do Minecraft com Forge 1.7.10.
* Não use classes, métodos ou eventos que não existam no Forge 1.7.10.

Processo de implementação:

1. Primeiro, inspecione o projeto existente, caso exista.
2. Apresente a estrutura de arquivos planejada.
3. Explique resumidamente a arquitetura.
4. Configure um projeto Forge 1.7.10 compilável.
5. Implemente os componentes básicos.
6. Implemente a descoberta das redes.
7. Implemente o simulador de circuito em série.
8. Implemente os estados visuais.
9. Implemente o multímetro.
10. Compile e corrija todos os erros encontrados.
11. Forneça instruções de execução e teste.

Não entregue pseudocódigo ou classes incompletas. Gere arquivos completos, com package e imports corretos.

Se alguma funcionalidade não puder ser concluída, não a substitua silenciosamente por uma simulação falsa. Explique a limitação e mantenha o projeto compilável.

Critérios de aceitação:

* O projeto compila com Java 8.
* O mod abre no Minecraft Forge 1.7.10.
* Todos os blocos aparecem em uma aba criativa chamada Craftônica.
* É possível montar um circuito em série no mundo.
* O LED acende com fonte, GND, botão fechado e polaridade correta.
* O LED não acende quando estiver invertido.
* O LED não acende com circuito aberto.
* O resistor de 220 Ω produz aproximadamente 13,64 mA no exemplo de 5 V.
* O multímetro apresenta os valores no chat.
* Um circuito sem resistor provoca sobrecorrente.
* O LED pode queimar e seu estado continua salvo após recarregar o mundo.
* Redes separadas funcionam independentemente.
* Circuitos não suportados são detectados sem travar o jogo.
* Colocar ou remover fios não causa recursão infinita.
* O servidor permanece responsável pelos cálculos.

Ao terminar, apresente:

* arquivos criados;
* arquitetura implementada;
* comandos usados para compilar;
* resultado da compilação;
* roteiro manual para testar cada critério;
* limitações conhecidas;
* proposta da próxima etapa.

Comece agora pela análise da estrutura existente e pela configuração compilável do projeto. Avance em etapas, compilando após cada mudança importante.

## Context
No additional project context was provided.

## Working Instructions
Inspect the repository before editing, keep changes minimal, and verify the affected behavior.

## Initialization
This workspace started from an initial idea through AI-guided onboarding. The Master must clarify scope, constraints, success criteria, and the first mission with the user before any agent edits files.

## Teagarden
This project is operated from Teagarden, a desktop cockpit for workspaces, missions, panes, providers, handoffs, and optional multi-agent orchestration. Treat the selected workspace directory as the project boundary. Use mission context and structured handoffs when coordinating agents; never expose credentials or grant renderer code direct filesystem, PTY, or secret authority.

## OverClick Policy
Use OverClick for every implementation task: claim the card before editing, work on its registered branch, run verification, push a commit, and deliver evidence.
