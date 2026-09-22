# Simulação articulada no servidor

O perfil articulado mantém a pose mundial do corpo raiz no modelo terrestre e
armazena somente posição e velocidade generalizadas das juntas filhas. A pose de
cada corpo é recomposta em ordem pai–filho a cada subpasso de 25 ms; transformações
de corpos não são persistidas como uma segunda autoridade.

## Dinâmica

Para cada junta, o servidor deriva do manifesto:

- massa total do subgrafo filho;
- centro de massa transformado;
- inércia principal projetada no eixo da junta;
- termo de eixo paralelo em relação ao pivô físico;
- esforço gravitacional generalizado;
- atrito, velocidade, esforço e reação máxima do perfil.

Juntas prismáticas usam a massa móvel do subgrafo. Juntas rotativas usam a inércia
refletida no eixo. O integrador semi-implícito recebe torque ou força, nunca uma
posição alvo. Batentes, contato e colisão zeram a velocidade que avançaria contra a
restrição sem deslocar a coordenada para o outro lado.

## Servo

Um servo conectado diretamente à entrada rotativa de uma junta usa a ligação
elétrica extraída da montagem e aceita apenas o pulso Timer1 validado em D9 ou D10.
O controlador calcula torque limitado por alimentação, corrente e temperatura. A
posição medida vem da junta; a posição calculada internamente pelo servo não
substitui a coordenada física. Gravidade e reação do batente retornam como carga,
mantendo corrente e aquecimento durante stall.

Sem alimentação o torque é zero. O perfil inicial usa explicitamente `HOLD` para
perda de sinal enquanto houver alimentação. Estado térmico, alvo, idade do sinal,
carga e diagnóstico são persistidos dentro do envelope checksummed.

## Colisão e contato

Cada corpo possui volumes derivados apenas de seus módulos. O servidor transforma
os oito cantos de cada volume e testa a união entre pose anterior e proposta para
evitar tunnelling. Corpos vizinhos ligados pela própria junta são excluídos do teste
de autocolisão; pares não adjacentes são testados conservadoramente.

Consultas ao mundo verificam todos os chunks antes de colisão. Fronteira
descarregada mantém o último estado e adia o passo, sem solicitar carregamento. Uma
colisão conserva a última coordenada válida e zera velocidades articulares.

## Orçamento

Uma reserva inclui todos os corpos, juntas e pares de colisão antes de alterar o
estado. O perfil por tick aceita até 64 passos de corpo, 62 passos de junta e 256
testes conservadores, limitados também a 128 por subpasso. Como há dois subpassos,
uma árvore máxima de 32 corpos/31
juntas cabe apenas quando sua complexidade de colisão também cabe. Caso contrário,
o passo inteiro retorna `BUDGET_EXCEEDED`; não existe atualização parcial.

## Sincronização e renderização

O payload visual versão 2 contém somente módulos renderizáveis, IDs de corpo e a
árvore mínima de juntas. Posição e velocidade são publicadas pelo `DataWatcher` em
um codec limitado a 31 juntas. O cliente recompõe matrizes para desenhar os corpos,
mas não envia coordenadas, esforços nem colisões ao servidor.

## Limites atuais

- o acoplamento automático de servo desta etapa é direto entre `output` e `drive`;
  o solver já aceita esforços refletidos, mas resolução genérica de uma cadeia de
  engrenagens até uma junta ainda deve reutilizar o grafo de transmissão;
- contato articulado usa bloqueio inelástico conservador, sem restituição;
- o corpo raiz permanece no modelo terrestre sem pitch/roll; reações fora do plano
  são tratadas como suportadas pela estrutura/solo do perfil terrestre.
