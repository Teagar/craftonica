# Gate de desempenho e acessibilidade 1.0

## Orcamentos

| Recurso | Limite |
| --- | ---: |
| Blocos por rede | 1.024 |
| Redes resolvidas por tick | 4 |
| Incognitas MNA somadas por tick | 512 |
| Pacotes do editor processados por tick | 32 |
| Pacotes do editor enfileirados por jogador | 8 |
| Amostragem de sensor/atuador | 1 a cada 4 ticks |
| Historico Serial | 8 KiB |
| Fonte do editor | 32 KiB |

Sensores e atuadores distribuem a fase de amostragem pelas coordenadas, evitando
picos quando muitos blocos sao carregados juntos. Consultas de ramo usam indice
por posicao publicado com o resultado da rede. O editor mantem em cache o tamanho
UTF-8 e o indice de linhas, sem reconstruir ambos a cada quadro.

## Matriz de acessibilidade

| Fluxo | Visual sem depender de cor | Texto | Teclado | Audio |
| --- | --- | --- | --- | --- |
| Estado eletrico | Texturas distintas para aberto, fechado, queimado, anodo e catodo | Diagnostico do multimetro | Interacao vanilla | Clique, fechamento e falha possuem sons distintos |
| RoboBoard | Estado e falha escritos no editor | Status, compilacao e diagnosticos localizados | `Ctrl+S`, `F5`, `F6`, `F7`, `Ctrl+L`, setas e edicao completa | Feedback eletrico do circuito permanece disponivel |
| Editor em GUI pequena | Acoes e abas em duas linhas abaixo de 400 px | Status e contador nao se sobrepoem | Todas as acoes principais possuem atalho | Nao exige audio |
| LED e polaridade | Corpo ligado/queimado e simbolos de terminais usam texturas diferentes | Multimetro informa polaridade e sobrecorrente | Interacao vanilla | Transicoes e falhas possuem sons distintos |

O buzzer e o motor continuam cargas eletricas educacionais sem simulacao acustica
ou mecanica, conforme o escopo documentado. Nenhum diagnostico depende apenas
desses efeitos.

## Confirmacao

- `SamplingBudgetTest` confirma frequencia e distribuicao de fase.
- `EditorDocumentTest` confirma o cache UTF-8 atraves de edicao, undo e redo.
- `electricalProfile` mantem o cenario nodal maximo dentro dos limites publicados.
- A validacao no Prism deve cobrir GUI pequena, atalhos e carregamento do mundo.
