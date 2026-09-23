# Soak e orçamento da robótica 2.0

Esta validação cobre frotas heterogêneas formadas pelos mesmos manifests públicos
usados nos gates de 2WD, 4WD, esteiras, mecanum, braço articulado e atuador linear.
O perfil não usa tempo de parede para decidir física: tempo de CPU, heap e GC são
medidos apenas para rejeitar regressões grosseiras e produzir evidência comparável.

## Como executar

```bash
./scripts/gradle-java8.sh roboticsSoakProfile roboticsFailureProfile
```

O primeiro task gera:

```text
build/reports/runtime/crl-95-soak.txt
```

`roboticsFailureProfile` executa os casos de workers reais sem ativar o gate de
compilação AVR. Assim, a validação de isolamento continua executável mesmo quando
o ambiente local recusa o digest de `/usr/bin/bash`.

## Budgets obrigatórios

| Recurso | Limite |
|---|---:|
| Robôs modulares admitidos por dimensão/tick | 64, sem fila |
| Workers AVR | 4 |
| Fila de runtime | 64 |
| CPU por robô-tick no gate | 2 ms |
| Parede por robô-tick no gate | 5 ms |
| Heap retido adicional | 256 MiB |
| Payload visual por robô | 8.192 bytes |
| Colisões por subpasso articulado | 128 |
| Corpos/juntas/colisões por tick articulado | 64 / 62 / 256 |

Os limites de CPU e parede são tetos de segurança para máquinas de CI, não metas
de gameplay. O custo observado deve ser registrado no relatório a cada release.

## Baseline observado em 22 de setembro de 2026

| Perfil | Robôs | Ticks | CPU total | Parede | Heap retido | GC | Payload para 2 clientes |
|---|---:|---:|---:|---:|---:|---:|---:|
| pequeno | 4 | 1.000 | 30,5 ms | 31,0 ms | 0 B | 2 | 2.982 B |
| médio | 16 | 2.000 | 408,6 ms | 421,3 ms | 6.368 B | 7 | 10.958 B |
| limite | 64 | 4.000 | 2.089,2 ms | 2.116,0 ms | 0 B | 13 | 42.862 B |

O maior snapshot individual teve 428 bytes, com no máximo quatro contatos e 27
volumes de colisão. O perfil limite executou 256 mil robô-ticks, 3.808.140 testes
de chunk carregado e o mesmo número de consultas de colisão. Nenhuma consulta
solicitou carregamento de chunk.

Braços que alcançaram autocolisão foram parados explicitamente pelo solver; a
frota e os demais mecanismos continuaram. O contador aparece no relatório para
que uma mudança geométrica não transforme essa contenção em custo invisível.

## Falhas injetadas

| Falha | Confirmação |
|---|---|
| Worker bloqueado/morto | timeout encerra o processo e a submissão seguinte usa worker substituto |
| Fila cheia | a 69ª identidade é recusada; quatro workers e 64 posições não crescem |
| NBT adulterado | 256 envelopes são recusados e o envelope válido continua legível após cada tentativa |
| Colisão prolongada | 20 mil sweeps permanecem `BLOCKED`, sem atravessar o mundo |
| Chunk ausente | retorna `UNLOADED` antes de consultar colisão e sem pedido de carga |
| Sobrecarga de frota | seleção circular determinística limita 64 robôs por tick, sem acumular backlog |

Falhas pertencem ao robô ou worker que as produziu. Nenhum caso exige reiniciar o
servidor, reutiliza estado adulterado ou permite que o cliente amplie budgets.
