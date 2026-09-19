# CRL-26: Gate da simulacao 0.3.5

- Base revisada: `fab6793` (0.3.3)
- Versao resultante: 0.3.5
- Ambiente: Java 8, Forge 10.13.4.1614, Minecraft 1.7.10
- RFC normativa: [`../rfc/0001-arquitetura-nodal-dc-0.3.md`](../rfc/0001-arquitetura-nodal-dc-0.3.md)

## Resultado

O gate foi aprovado depois das correcoes abaixo. A simulacao continua
deterministica por unidades de trabalho, sem limite baseado em relogio, e nao
publica grandezas de redes incompletas ou invalidas.

## Falhas corrigidas

- Chaves fechadas deixavam de ser ramos mensuraveis porque seus terminais eram
  colapsados como condutor ideal durante a extracao.
- O cache podia publicar valores consumiveis quando a extracao, a referencia ou
  a topologia continham erro.
- Consultas de terminal ausente podiam produzir `0 V` em vez de ausencia
  explicita.
- O limite matricial por rede estava em 512, acima das 256 incognitas definidas
  pela RFC.
- O fingerprint nao incluia estado eletrico nem grupos condutores e podia
  reutilizar uma topologia obsoleta.
- A ordem de `NodeId` derivada de texto nao era numerica para coordenadas
  negativas.
- Diodos recebiam diagnosticos de gameplay exclusivos de LED.
- Chaves e disjuntores de baixa resistencia eram classificados como curto sem
  considerar o caminho da fonte.
- O LED podia atualizar brilho ou queima enquanto a rede estava sem solucao
  valida.
- O feedback audiovisual assumia resultado local valido e derrubava o servidor
  integrado quando uma rede invalida retornava ausencia explicita.
- Fronteiras de chunks nao participavam da invalidacao no carregamento.
- O multimetro removia o sinal da tensao e calculava resistencia a partir do
  circuito energizado. A resistencia e a continuidade agora usam uma fonte-teste
  de `1 V`, com fontes independentes zeradas, e rejeitam redes com LED ou diodo.

## Gates automatizados

- 84 testes JUnit no total, sem falhas.
- 11 testes de gate eletrico para MNA, LU, limites, referencia analitica,
  determinismo e medicao auxiliar.
- Caso de referencia com fonte 5 V/10 ohms, resistor 220 ohms e LED 2 V/1 ohm:
  `12,987012987 mA` e `2,012987013 V` no LED.
- 1.024 fios colapsam em um no; 1.025 blocos retornam limite explicito.
- 256 incognitas sao aceitas; 257 retornam `MATRIX_LIMIT` antes da solucao.
- Correntes orientadas, potencia absorvida/fornecida, pivôs, residuos e entrada
  nao finita possuem testes dedicados.
- 10 testes do adaptador MCP passaram.

## Perfil deterministico

| Recurso | Limite |
| --- | ---: |
| Blocos por rede | 1.024 |
| Terminais | 6.144 |
| Ramos | 2.048 |
| Incognitas MNA por rede | 256 |
| Celulas da matriz densa | 65.536 |
| LEDs ativos | 64 |
| Iteracoes nao lineares | 16 |
| Redes resolvidas por tick | 4 |
| Incognitas somadas por tick | 512 |

A busca de contatos usa indice por posicao/face e custo `O(terminais)`. A fila,
os IDs, o union-find, os pivôs e os fingerprints usam ordenacao canonica.

## Limites conhecidos

- O backend continua denso e limita cada rede a 256 incognitas; redes maiores
  precisam de um backend esparso futuro.
- Resistencia e continuidade em redes com LED ou diodo retornam diagnostico de
  medicao nao suportada; nenhuma resistencia incremental e inventada.
- O modelo e DC em regime permanente, sem CA, transientes, capacitores,
  indutores ou transistores.
- A validacao de servidor dedicado permanece coberta pelo isolamento de APIs e
  pelo JAR reobfuscado; o gate de gameplay principal ocorre na instancia Prism.

## Validacao no Prism

- O JAR `craftonica-0.3.5.jar` foi instalado e identificado na lista de mods.
- O mundo legado `New World`, que reproduziu o crash de feedback em 0.3.4,
  carregou normalmente e permaneceu com o servidor integrado ativo.
- O multimetro marcou as duas pontas e recusou de forma explicita uma medicao
  ambigua em um ramo com varios terminais, sem publicar um valor inventado.
- O `latest.log` ficou sem excecoes do mod; apenas o erro conhecido da integracao
  Twitch removida do Minecraft 1.7.10 foi registrado na inicializacao.

## Comandos

```sh
./scripts/gradle-java8.sh clean test build verifyReleaseJar
./scripts/gradle-java8.sh electricalProfile
python3 -m unittest test_craftonica_mcp.py  # em tools/mcp
./scripts/install-prism.sh
```
