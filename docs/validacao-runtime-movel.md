# Validação do runtime móvel

Este documento registra os gates automatizados e o roteiro manual do `CRL-66`.
Os testes automatizados fazem parte de `verifyRuntimeGates`; nenhum deles usa o
cliente para decidir física, sonar, GPIO ou colisão.

## Limites executáveis

| Recurso | Limite |
|---|---:|
| Workers AVR simultâneos | 4 |
| Heap por worker | 64 MiB |
| Memória máxima do scope por worker | 256 MiB, sem swap |
| Pedidos aguardando worker | 64 |
| Hosts simultâneos admitidos | 68 |
| Ciclos AVR por quadro móvel | 800.000 |
| Quantum do protocolo | 50.000 ciclos |
| Timeout por worker | configurado pelo servidor |
| TX por quantum | 256 bytes |
| TX por lote | 4.096 bytes |
| Histórico Serial por placa | 8.192 bytes |
| Alterações GPIO/PWM por resultado | 64 |
| Subpassos mecânicos por tick | 2 × 25 ms |
| Alcance de tracking da entidade | 96 blocos |
| Frequência de atualização | 2 ticks |

`RuntimeStressGateTest` envia 16 identidades móveis concorrentes e exige retorno
isolado até 800 mil ciclos, usando no máximo quatro processos. Outro cenário ocupa
quatro workers e 64 posições de fila com processos bloqueados e exige rejeição da
69ª identidade, sem crescimento de estruturas internas.

`MobileRobotStressGateTest` executa duas frotas idênticas de 32 robôs por 10 mil
subpassos e compara posição, rumo e velocidades bit a bit. Também realiza 128
ciclos de unload/NBT/reload com firmware em execução e 256 casos de NBT adulterado.
Identidade e proprietário não mudam; gerações crescem; dados inválidos entram em
`QUARANTINED` e não retomam firmware.

O gate de servidor dedicado inspeciona o constant pool das classes autoritativas e
recusa referências a `net.minecraft.client` ou `br.com.craftonica.client`. O
movimento verifica `chunkExists` antes de deslocar a caixa de colisão e para sem
solicitar ou carregar chunks ausentes.

O relatório reproduzível é gerado em:

```text
build/reports/runtime/crl-66-profile.txt
```

## Ensaio manual integrado

Validado em mundo local Forge 1.7.10:

1. `/craftonica arena create` executado duas vezes na mesma origem;
2. chassi provisório removido sem peças por `/craftonica robot remove-test`;
3. sketch autônomo carregado e compilado em uma RoboBoard;
4. firmware copiado e iniciado no robô sobre a área verde;
5. estado permaneceu `RUNNING`, sem fault, enquanto a Serial avançou de 103 para
   248 e 369 bytes;
6. corredor inicial maior que 400 cm provocou o fluxo seguro de timeout e
   varredura, sem avanço cego.

## Matriz manual dedicada/multiplayer

Esta matriz exige aceitar a EULA do servidor local e abrir duas contas/clientes;
por isso não é iniciada automaticamente pelo build.

1. Inicie `runServer` com o mesmo JAR e um mundo de cópia.
2. Conecte dois clientes; deixe o cliente B apenas observando.
3. No cliente A, inicie firmware e registre a pose a cada 20 ticks.
4. Confirme no console do servidor que a pose, colisão e Serial correspondem ao
   observado por ambos os clientes; o cliente B não deve causar decisões extras.
5. Afaste ambos os clientes até descarregar o chunk, retorne e confira uma única
   entidade, identidade igual e geração maior.
6. Encerre forçadamente um processo `craftonica-runtime-worker`, confirme fault
   contido e depois inicie outra placa para verificar substituição do worker.
7. Repita com 4, 16 e 68 robôs. A 69ª execução simultânea deve receber
   `RUNTIME_BUSY`, sem crescimento de fila, memória ou chunks carregados.

Registre TPS, heap, bytes de Serial, colisões, timeouts e divergências. Uma
aprovação manual só é válida quando não houver divergência entre clientes e
servidor e nenhum chunk for carregado pelo movimento ou sonar.
