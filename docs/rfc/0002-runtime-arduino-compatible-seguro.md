# RFC 0002: runtime Arduino-compatible seguro

- Status: aceita
- Escopo: Craftônica 0.5
- Alvo: Arduino Uno R3, ATmega328P, Arduino AVR Core 1.8.6
- Dependências: simulação nodal 0.3 e ensino 0.4

## Resumo

O Craftônica aceitará sketches C/C++ reais compatíveis com um perfil documentado
do Arduino Uno R3. O servidor compilará fontes em um processo isolado usando um
toolchain AVR imutável, verificará o artefato sem confiar no compilador e executará
o firmware em um interpretador AVR determinístico fora da JVM do Minecraft.

Sketches nunca serão executados como binário nativo do host, JNI, bytecode JVM,
JavaScript ou DSL proprietária. A thread de tick apenas publica entradas
imutáveis e aplica lotes de saída já concluídos e validados. Trabalho atrasado
faz a placa ficar atrás do tempo virtual; nunca bloqueia, pula ciclos ou inventa
estado elétrico.

Os termos DEVE, NÃO DEVE, DEVERIA e PODE são normativos.

## Decisões

1. A compatibilidade é de fonte e comportamento com o perfil publicado, não uma
   promessa de emular todo o hardware Uno.
2. O Core 1.8.6 é uma dependência de terceiros fixada; integração, verificador,
   bridge e interpretador são clean-room. Um shim versionado envolve APIs de pin
   antes de delegar ao core; fontes e patch entram no manifesto.
3. O compilador produz AVR ELF/HEX. Um verificador independente converte o ELF
   hostil para o formato canônico `CRLFirmware`; somente esse formato é executável.
4. O runtime interpreta instruções AVR. JIT, QEMU com tradução nativa, JNI e
   geração de classes JVM são proibidos.
5. Compilação e execução ocorrem em workers separados, assíncronos, descartáveis
   e sem autoridade sobre mundo, jogadores ou rede elétrica.
6. O servidor é a única autoridade para fontes, revisões, firmware ativo,
   checkpoints, entradas, saídas e efeitos no mundo.
7. Todos os limites falham de forma explícita. Firmware anterior continua ativo
   após falha de compilação; artefato parcial nunca é ativado.

## Identidade do perfil

O perfil inicial se chama:

```text
craftonica-avr-uno:1/core-1.8.6/gcc-7.3.0-atmel3.6.1-arduino7
```

- MCU: `atmega328p`.
- Clock: `F_CPU=16000000L`.
- Variante: `standard` do Arduino AVR Core 1.8.6.
- Macros: `ARDUINO_AVR_UNO`, `ARDUINO_ARCH_AVR` e `ARDUINO=10819`.
- Linguagem: AVR GNU C++11.
- Flags normativas: `-std=gnu++11 -Os -fpermissive -fno-exceptions
  -ffunction-sections -fdata-sections -fno-threadsafe-statics
  -Wno-error=narrowing`. LTO fica desabilitado no perfil 1 para preservar limites
  de tradução, símbolos de ABI e auditoria do verificador.
- Limites físicos: 32.256 bytes de programa, 2.048 bytes de SRAM e 1.024 bytes
  de EEPROM.
- Manifesto: versões e SHA-256 do compilador, binutils, avr-libc, core, variante,
  preprocessor, bibliotecas e configuração de sandbox.

No protocolo, `profileHash` é SHA-256 dos bytes UTF-8 do profile ID acima, sem
NUL/newline: `d181b87d807dcddc429c130699245cfb38c410fc7c1898323e4d263fbbe9ef36`.
O manifesto completo tem hash próprio e ordena chaves UTF-8 bytewise em JSON
canônico RFC 8785; CRL-30 publica ambos, mas CRL-31 usa o profileHash normativo.

Nenhum job consulta `latest`, baixa dependência ou lê instalação Arduino do host.
Mudança em qualquer digest cria outro perfil e invalida cache/checkpoint.

## Contrato C/C++

O código é o subconjunto aceito pela invocação AVR GNU C++11 fixada, não uma
“linguagem Arduino” reimplementada. São preservados:

- `char` de 8 bits;
- `short`, `int`, `unsigned int`, `size_t` e ponteiros de dados de 16 bits;
- `long` de 32 bits e `long long` de 64 bits;
- `float` e `double` de 32 bits;
- wrap módulo `2^N` para inteiros sem sinal;
- inicialização global/estática do runtime AVR.

Exceções e RTTI ficam desabilitadas com `-fno-exceptions -fno-rtti`.
Comportamento indefinido C/C++ não ganha
semântica Java: uma imagem compilada continua determinística no interpretador,
mas não há garantia de resultado pedagógico. Acesso inválido detectável produz
falha estruturada da placa, nunca acesso à memória do host.

Headers `.h` de fonte dentro do bundle são permitidos. Headers externos,
bibliotecas pré-compiladas, objetos, archives, shared objects, classes e plugins
enviados pelo jogador são proibidos. Não existem filesystem, sockets, processos,
ambiente, relógio do host ou entropia do host para o sketch.

## Bundle e preprocessing

- Máximo de 16 arquivos, 64 KiB totais e 32 KiB por arquivo.
- Extensões iniciais: `.ino`, `.h`, `.c` e `.cpp`.
- Nome principal ASCII seguro e arquivo principal `<nome>.ino` obrigatório.
- Caminhos relativos canônicos; NUL, `..`, absoluto, symlink e nomes duplicados
  após normalização são rejeitados antes do worker.
- O arquivo principal vem primeiro; demais `.ino` seguem ordem bytewise.
- `#include <Arduino.h>` é inserido quando ausente.
- Protótipos seguem `craftonica-sketch-preprocessor:1`: após concatenação e
  `Arduino.h`, um parser de tokens identifica funções no namespace global, ignora
  métodos/templates/lambdas e funções já declaradas, e insere declarações antes
  da primeira função. Caso ambíguo exige protótipo explícito. `.c/.cpp/.h` não
  recebem transformação.
- `#line` preserva arquivo e linha lógicos. Diagnósticos nunca revelam caminho do
  sandbox ou servidor.
- Includes resolvem somente no bundle, core, variante, avr-libc e bibliotecas
  explicitamente aprovadas e versionadas.
- Assembly, inline assembly, response files, opções de linker e pragmas/plugins
  controlados pelo usuário são rejeitados no perfil 1.

O sketch DEVE definir `void setup()` e `void loop()`. A sequência é a do core:
startup AVR, construtores estáticos, `init()`, `initVariant()`, `setup()`, e então
`loop()` seguido de `serialEventRun()` repetidamente. `setup()` executa uma vez por
power-on/reset; iterações de `loop()` não são equivalentes a ticks do Minecraft.

## API garantida

### Constantes e tipos

`HIGH`, `LOW`, `INPUT`, `OUTPUT`, `INPUT_PULLUP`, `LED_BUILTIN`, `A0`–`A5`,
`DEC`, `HEX`, `OCT`, `BIN`, `byte`, `boolean`, `word` e tipos inteiros fixos.

### GPIO

- `pinMode`, `digitalRead` e `digitalWrite` em D0–D13 e A0–A5/D14–D19.
- `digitalWrite(HIGH)` em entrada habilita pull-up; `LOW` desabilita.
- D0/D1 compartilham estado real com UART; D13 compartilha com LED embutido.
- Pin dinâmico inválido causa `INVALID_PIN`. O patch substitui os próprios
  entrypoints públicos do core por versões validadas; os entrypoints inseguros
  não existem no archive linkável. O ELF inclui nota de ABI e o verificador
  rejeita símbolo externo ou exportado fora da allowlist do perfil.
- Em argumento inválido, o shim executa `STS 0xC1F0,r24` com `r24=1`. Esse
  endereço virtual não pertence ao ATmega328P: a VM o reconhece como trap ABI 1
  e produz `INVALID_PIN` antes de alterar CPU/MMIO. Outros valores são rejeitados
  como `UNSUPPORTED_ABI_TRAP`; invocação direta pelo sketch só falha sua placa.
- Entrada digital: até 1,5 V é `LOW`, a partir de 3,0 V é `HIGH`; entre limites
  retorna último valor estável, inicialmente `LOW`, e publica diagnóstico
  `INDETERMINATE_INPUT`.

### ADC e PWM

- `analogRead(0..5)` e `analogRead(A0..A5)` retornam `0..1023`. O snapshot codifica
  tensão como microvolts `i32`; NaN/infinito não existem. A conversão inteira é
  `clamp(floor(microvolts * 1024 / 5000000), 0, 1023)`.
- `analogWrite` oferece PWM em D3, D5, D6, D9, D10 e D11.
- Valor 0 fixa `LOW`; 255 fixa `HIGH`; demais valores geram PWM de 8 bits.
- D5/D6 usam comportamento Timer0 fast PWM (~976,56 Hz); D3/D9/D10/D11 usam
  phase-correct (~490,20 Hz).
- Em pin não PWM, abaixo de 128 é `LOW` e 128 ou mais é `HIGH`.
- Como no core, `analogWrite(int)` trata `<=0` como LOW e `>=255` como HIGH.
- ADC lê somente snapshot elétrico servidor-autoritativo. Ruído e não linearidade
  física não são inventados no perfil 1.

### Tempo

- `millis`, `micros`, `delay` e `delayMicroseconds` usam ciclos e Timer0 virtuais.
- Nunca usam relógio Java, wall clock ou `World#getTotalWorldTime`.
- `micros()` tem granularidade de 4 us e wrap unsigned de 32 bits (~71,58 min).
- Timer0 transborda a cada 1,024 ms; `millis()` preserva a correção fracionária,
  com
  wrap unsigned de 32 bits (~49,71 dias).
- `delay()` preserva o busy loop do core AVR: consome ciclos virtuais enquanto
  timers/interrupções avançam, sem dormir ou ocupar thread do Minecraft.
- Pausa, chunk unload e host lento não avançam tempo por wall clock.

### Serial

Garantido: `begin`, `end`, `available`, `availableForWrite`, `peek`, `read`,
`write`, `print`, `println`, `flush` e `operator bool`.

- Formato padrão 8N1; `begin(baud)` calcula UBRR/U2X como o core. Qualquer divisor
  representável é emulado com seu baud efetivo, não normalizado ao nominal.
- RX/TX preservam rings de 64 bytes do core, com capacidade útil de 63 bytes.
- RX cheio descarta byte novo. TX cheio bloqueia apenas CPU virtual até a UART
  liberar espaço. `flush` espera no tempo virtual.
- `read`/`peek` vazios retornam `-1`; `println` termina em `\r\n`.
- Histórico visual separado é limitado a 8 KiB por placa. Truncamento gera
  diagnóstico e nunca altera a UART virtual.
- Entrada Serial recebe timestamp virtual explícito e ordem estável.

`random`/`randomSeed` preservam o estado inicial e algoritmo do core/avr-libc; o
estado é persistido e nunca recebe entropia do host.

## Superfície inicialmente não suportada

`SPI`, `Wire`/I2C, EEPROM pública, Servo, SoftwareSerial, USB HID, SD, rede,
`tone`, `pulseIn`, `shiftIn/shiftOut`, interrupções externas/pin-change, watchdog,
sleep, brown-out, bootloader, fuses, self-programming, debugWire, comparador,
AREF externa e bibliotecas de terceiros.

Header não aprovado falha com `UNSUPPORTED_LIBRARY`. Acesso a periférico não
implementado falha com `UNSUPPORTED_PERIPHERAL` e para somente aquela placa. Nada
é aceito silenciosamente. Servo será um capability posterior porque altera
Timer1 e PWM D9/D10.

O sketch pode usar registradores GPIO, Timer0/1/2, ADC e UART0. A VM implementa
modos com clock interno, prescalers, compare, flags e interrupções conforme
datasheet. Timer0/1 com clock externo, input capture externo, Timer2 assíncrono,
ADC com referência externa e UART síncrona são configurações não suportadas;
escrita que as habilitaria causa `UNSUPPORTED_PERIPHERAL` antes de mutar estado.
As frequências acima descrevem `analogWrite`, não restringem modos internos.
Leitura ou escrita de watchdog, EEPROM, SPI, TWI, comparador, self-programming ou
endereços reservados causa `UNSUPPORTED_PERIPHERAL` antes de mutar estado.

## Pipeline de compilação

```text
cliente -> validação/autorização no servidor -> fila limitada
        -> supervisor isolado -> avr-g++/linker fixados
        -> ELF hostil + manifesto autenticado
        -> verificador independente -> CRLFirmware canônico
        -> cache imutável -> ativação atômica
```

O servidor envia fontes exatas, revision, profile ID e request UUID. O supervisor
executa binários fixos com argumentos fixos, sem shell. O cache key é SHA-256 de
protocolo, perfil, digests, flags e tuplas ordenadas `(nome, bytes)`. Fonte não é
normalizada.

Ambiente reproduzível: locale/timezone/`SOURCE_DATE_EPOCH` fixos, archive
determinístico, prefix maps, seed do compilador e ordem de arquivos fixos.
Builds repetidos devem produzir `CRLFirmware` byte a byte idêntico; diferenças
irrelevantes do ELF não atravessam a canonicalização.

A receita usa `avr-gcc` para `.c`, `avr-g++` para `.cpp` e a translation unit do
sketch, e `avr-g++` no link. Todas as etapas recebem `-mmcu=atmega328p`,
`-DF_CPU=16000000L` e as macros do perfil. Core e variante são compilados em
ordem bytewise. O link recebe objetos do bundle, archive determinístico do core,
`-Wl,--gc-sections` e o mapa fixo do ATmega328P; `avr-objcopy` gera HEX
somente para diagnóstico. O manifesto registra argv e ordem completas.

### Sandbox do compilador

- UID sem privilégio, `no_new_privs`, capabilities removidas.
- Namespaces Linux de user/PID/mount/IPC/network e cgroup v2 próprio. O servidor
  recusa habilitar compilação se namespaces, cgroup, seccomp ou `no_new_privs`
  não puderem ser comprovados. Serviço público exige microVM.
- Toolchain/core somente leitura; workspace `tmpfs` novo e destruído por job.
- Sem home, save, diretório do jogo, segredos, Docker socket ou SSH agent.
- Sem rede e com seccomp negando sockets, mount, ptrace, BPF, perf, keyring,
  devices e criação arbitrária de namespaces.
- Processos somente do toolchain fixo; `execve` alcança apenas paths read-only
  cujos digests constam no manifesto. O grupo inteiro é morto no cancelamento.

| Recurso de compilação | Limite padrão |
| --- | ---: |
| CPU / wall time | 5 s / 10 s |
| RSS / address space | 256 MiB / 512 MiB |
| tmpfs / arquivo de saída | 32 MiB / 2 MiB |
| processos / FDs | 32 / 64 |
| preprocessor output | 2 MiB |
| include depth | 32 |
| diagnósticos | 16 KiB ou 100 entradas |
| jobs pendentes | 1 por jogador, 32 global |
| submissão | burst 2, depois 6/min por jogador |

Timeout mata grupo após 250 ms de graça. O servidor nunca aguarda término.
Excesso de wall time é `COMPILER_UNAVAILABLE`, diagnóstico operacional que não
ativa cache negativo nem altera firmware. `COMPILE_LIMIT` fica reservado a
limites estruturais e CPU contabilizada do job.

## Formato `CRLFirmware` 1

Inteiros e endereços são little-endian e byte-addressed. O header packed de 148
bytes não contém padding:

```text
0 magic[4]="CRLF"       4 version:u16=1       6 headerLength:u16=148
8 totalLength:u32       12 segmentCount:u16   14 reserved:u16=0
16 instructionAbi:u16=1 18 mmioAbi:u16=1
20 profileHash[32]      52 sourceHash[32]      84 payloadHash[32]
116 firmwareHash[32]
```

`profileHash` é a constante normativa derivada do profile ID; `sourceHash`, das tuplas
`(nomeLength:u16,nome,contentLength:u32,bytes)` ordenadas. Após o header vem a
tabela com no máximo 8 segmentos:

```text
type:u8 flags:u8 reserved:u16 address:u32 length:u32 payloadOffset:u32
```

Tipos são `FLASH=1`, `SRAM_INIT=2` e `EEPROM_INIT=3`; outros são inválidos.
Perfil 1 exige `segmentCount=1`, exatamente um `FLASH` e `flags=0`, em zero, com
no máximo 32.256 bytes, comprimento par, vetores, código,
imagem de `.data` e PROGMEM. O power-on zera SRAM/EEPROM virtual e o startup AVR
copia `.data` da FLASH e zera `.bss`. Símbolos são sidecar diagnóstico separado,
não entram no `CRLFirmware`, cache runtime ou protocolo de execução.

Tabela e payloads têm alinhamento de 4 bytes; padding é zero e canônico. Offsets
são crescentes, não sobrepostos e contidos em totalLength, que termina no último
payload sem trailing bytes. Flags/reserved desconhecidos são rejeitados.
`payloadHash` cobre bytes `[148,totalLength)`. `firmwareHash` cobre todo o arquivo
com bytes `[116,148)` zerados. O entry point é o reset vector em flash zero. Não há relocation,
path, timestamp, string host ou seção arbitrária; startup, construtores, `.data`,
`.bss`, stack e vetores pertencem à imagem AVR linkada.

CRL-30 DEVE versionar um fixture binário mínimo e seu hash; CRL-31 DEVE consumir
os mesmos bytes como teste de contrato. ABI desconhecido é rejeitado antes da
ativação.

## Verificação

Assinatura/autenticação do worker prova origem, não correção. Um parser Java
limitado e com aritmética verificada DEVE:

1. recomputar hashes e conferir perfil/toolchain;
2. rejeitar seção desconhecida, overlap, relocation pendente, trailing bytes e
   comprimento inconsistente;
3. impor flash/SRAM/EEPROM do ATmega328P;
4. validar nota de ABI, allowlist de símbolos, segmentos e vectors; flash também
   contém PROGMEM e não pode ser decodificada cegamente como instrução;
5. exigir que o interpretador valide cada fetch alinhado e dentro de FLASH antes
   de decodificar, rejeitando opcode não suportado, `SPM` e debug;
6. rejeitar entrypoint fora de FLASH e configuração incompatível com o perfil;
7. emitir exatamente `CRLFirmware` 1 e recomputar seus hashes canônicos.

Fuzzing de mutação do parser e decoder é gate de release. O servidor não mantém
ELF como formato runtime.

## Runtime determinístico

Workers interpretam o conjunto de instruções ATmega328P, exceto `SPM` e debug,
em processo sem rede, filesystem gravável, subprocessos ou segredos. Não há JIT.
Cada requisição contém placa UUID+geração, firmware e
state hashes, revision, epoch, orçamento e snapshot de entrada. Resultado só é
aplicado se todos ainda coincidirem; resposta atrasada de placa removida,
reprogramada ou descarregada é descartada.

O estado contém 32 registradores, SREG, SP, PC word-addressed, SRAM/data space,
flash separada e MMIO para PORT/PIN/DDR B-C-D, Timer0/1/2, ADC e UART0. Cada
opcode custa os ciclos do datasheet. Interrupções são amostradas no fim da
instrução, respeitam bit I e prioridade do vetor, têm latência de quatro ciclos e
retornam por `RETI`. Fetch ímpar/fora de FLASH, MMIO reservado, SP fora da SRAM e
opcode inválido falham antes de mutar estado. Timers, prescalers, compare/overflow,
ADC, UART e GPIO avançam por ciclos; `LPM` lê PROGMEM normalmente.

Power-on começa com GPRs/SREG/ciclos zerados, PC=0, SP=`RAMEND=0x08FF`, SRAM e
EEPROM zeradas, pull-ups desligados e cada registrador MMIO no reset value da
Atmel ATmega328P datasheet 7810D–AVR–01/15. Zerar SRAM/EEPROM é divergência
determinística do silício. PINx reflete o primeiro snapshot; até ele, LOW. Reset
de firmware repete exatamente esse estado antes de executar o reset vector.

Determinismo lógico significa: mesmo firmware/profile, checkpoint e stream de
entradas timestampadas geram o mesmo trace em timestamps virtuais e hash final.
Wall time, locale, scheduling e ordem de mapas não participam. Timeout/crash do
host não altera estado lógico: o slice não é commitado e é reagendado em worker
novo. Após três falhas operacionais, supervisor marca worker/placa indisponível
para administração; isso não é fault do firmware nem entra no golden replay.

- Velocidade alvo: 16 MHz virtuais, 800.000 ciclos por tick, divididos em 16
  quanta absolutos de 50.000 ciclos. O worker termina a instrução que cruza o
  alvo absoluto; esse overshoot determinístico conta no quantum seguinte.
- Requisição pede um alvo absoluto e só retorna quantum completo. Deadline de
  25 ms cancela sem commit e reagenda o mesmo alvo; nunca encurta um slice.
- Uma placa tem no máximo um quantum em voo e acumula atraso explícito.
- Backlog acima de 16 milhões de ciclos impede novas ativações e pausa submissão
  de slices sem alterar checkpoint ou outputs. Operador pode resetar/desativar;
  carga do host nunca injeta transição lógica no firmware.
- Máximo padrão: 16 placas ativas/dimensão e 32/servidor. Configuração pode
  reduzir limites, nunca remover limites.
- Loop infinito é firmware AVR válido: completa quanta preemptíveis e não prende
  thread. Só exceder quota de output dentro de um quantum causa fault lógico.
- Operação cujo custo depende de entrada é cobrada e limitada; não há coleção
  host sem bound.

SRAM é exatamente 2.048 bytes; flash 32.256; EEPROM interna 1.024. Stack/heap
colidem conforme endereço AVR; SP fora da SRAM falha antes de acessar memória
host. O interpretador é iterativo e não usa call stack host para calls AVR.

Por quantum/placa: até 64 mudanças efetivas de GPIO/configuração de timer e 256
bytes TX. Arestas PWM estáveis não contam; cada
mudança de modo/prescaler/compare/COM emite segmento ordenado
`(início,fim,pin,timer,modo,prescaler,compare,fase)`. O bridge integra esses
segmentos determinísticos. Só writes GPIO redundantes são coalescidos. Valores são
validados e aplicados ao mundo somente no servidor, após o lote completo.

Runtime usa no máximo 4 workers, fila de 64 slices, cgroup de 1 CPU e 256 MiB por
worker, address space de 512 MiB, 32 FDs, 1 processo e resposta de 32 KiB. Worker
que viola protocolo/limite é morto e substituído. Por tick, no máximo 4 barriers
de rede e 64 KiB são validados/aplicados; excesso permanece pendente.

Placas ligadas à mesma rede elétrica usam barrier por `(worldTick,quantumIndex)`.
Só após todas concluírem o alvo, o servidor ordena lotes por
`(cicloVirtual,boardUUID,generation,requestSequence,outputOrdinal)`, aplica outputs,
resolve a rede e publica o snapshot seguinte. Estado VM e outputs do barrier são
um único commit: antes dele, nenhum checkpoint avança. Rede lenta mantém último
estado commitado; redes independentes não aguardam uma à outra.

### Sandbox do runtime

Runtime usa UID sem privilégio, `no_new_privs`, capabilities removidas, namespaces
user/PID/mount/IPC/network, root read-only sem diretório do jogo, tmpfs privado,
cgroup v2 e seccomp negando rede, exec, fork, mount, ptrace, BPF, perf e devices.
Core dumps e mappings executáveis graváveis são proibidos. Falha em comprovar
qualquer controle desabilita o runtime; serviço público exige microVM. Cancelar
mata o cgroup após 50 ms sem bloquear tick. Teste de integração demonstra cada
negação e reinício após worker hostil.

## Protocolo e autoridade multiplayer

Workers usam frames binários length-prefixed, versão explícita, request UUID,
tipo, tamanho, deadline, profile e digest. Java serialization é proibida. O
supervisor confiável autentica o canal por peer credentials e envelopa respostas
com MAC; worker sem segredos nunca calcula MAC.
Frames máximos: 128 KiB compilação/resposta, 64 KiB `FIRMWARE_LOAD` autenticado e
32 KiB slice runtime. Worker novo recebe `CRLFirmware` completo, confere digest e
mantém cache imutável; slice referencia esse digest. Versão/campo/tamanho
desconhecido falha antes de alocação. stdout/stderr não são protocolo.

O servidor valida autenticação, distância, chunk carregado, ownership/ACL, nonce
da sessão, revision esperada, rate e tamanho. Cliente nunca fornece firmware,
hash autoritativo, estado VM, pin output, contador ou resultado de compilação.
Edição usa optimistic locking: apenas uma transição `revision N -> N+1` vence.

Entradas elétricas são níveis `i32` amostrados uma vez por `(channel,worldTick)` e
podem coalescer somente ordinais do mesmo tick; o ciclo é `worldTick*800000`.
Serial/interações são eventos ordenados aceitos apenas se a fila persistível de
1.024 entries tiver espaço; fila cheia rejeita a ação com `INPUT_BACKPRESSURE`,
sem afirmar que entrou no stream. Nenhuma entrada aceita é descartada. Se o
histórico de níveis atingir 1.024 ticks por atraso operacional, a rede para de
aceitar novos quanta e preserva estado/output até recuperação administrativa.
Saídas carregam ciclo virtual e obedecem ao barrier, nunca são retroativas.

Reprogramar, resetar, remover, descarregar ou revogar permissão incrementa a
geração. Cancelamento remove fila ou marca geração stale; nunca usa `Thread.stop`
nem `join` no tick.

## Persistência

NBT versionado guarda somente dados limitados:

- UUID/geração e ACL da placa;
- fonte UTF-8 limitada, revision e digest;
- profile/toolchain/firmware hashes;
- registradores, PC, SP, SREG, SRAM, EEPROM e ciclos;
- timers, GPIO, ADC, UART, PRNG, interrupções e entradas pendentes;
- fault/suspension e último epoch aceito;
- checksum do checkpoint.

Schema desconhecido, campo oversized, checksum inválido, profile ausente ou
state/firmware mismatch desabilita a placa. Não há migração por adivinhação nem
fallback silencioso. Compilação nova só substitui firmware/checkpoint em uma
transação atômica após verificação completa.

Checkpoint só avança na mesma transação servidor que aplica o barrier e marca
seus outputs como publicados. Não existe resposta aceita aguardando aplicação.
Em save/unload, a geração sobe sem aguardar slice em voo e persistem último
barrier commitado, `appliedThrough`, sequência de request e journal de entradas
não consumidas. Resposta antiga é descartada; no reload o mesmo quantum é
reexecutado. Save durante `delay` ou UART produz o mesmo trace sem duplicar output.

## Cache

Cache é content-addressed, imutável e assíncrono. Só artefato verificado entra.
Hit reconfere digest, tamanho e perfil. Escrita é atômica sem seguir symlink.
Permissão e ativação nunca são cacheadas. Fonte/diagnóstico de um usuário não são
revelados a outro. Entry corrompida é removida e vira miss.

Padrão: 256 MiB, 10.000 entries, 1 MiB por registro; LRU fora do tick. Cache
negativo guarda apenas classificação estável por 60 s.

## Estados de falha

Falhas lógicas persistíveis: `COMPILE_REJECTED`, `COMPILE_LIMIT`,
`VERIFY_REJECTED`, `INVALID_PIN`, `UNSUPPORTED_LIBRARY`,
`UNSUPPORTED_PERIPHERAL`, `MEMORY_FAULT`, `FIRMWARE_OUTPUT_LIMIT` e
`CHECKPOINT_INCOMPATIBLE`. Diagnósticos operacionais não persistíveis:
`PROTOCOL_FAULT`, `WORKER_TIMEOUT`, `WORKER_UNAVAILABLE`, `COMPILER_UNAVAILABLE`,
`INPUT_BACKPRESSURE` e `STALE_RESULT`.

Falha lógica deixa outputs em alta impedância, exceto falha de nova compilação,
que mantém firmware anterior identificado. Diagnóstico operacional mantém último
output commitado e não altera trace lógico. Nenhum caso derruba outra placa.

## Casos de aceite obrigatórios

| Caso | Resultado obrigatório |
| --- | --- |
| Blink D13 com `delay(500)` | `setup` uma vez; trace virtual repetível e LED alternado |
| `for(;;){}` em `setup` ou `loop` | tick responsivo; preempção; firmware continua válido sem timeout lógico |
| recursão/heap até exaustão | falha virtual determinística; RSS host limitado |
| pin 255 | `INVALID_PIN`, sem OOB host |
| `millis`/`micros` perto do wrap | wrap unsigned e subtração elapsed corretos |
| ADC 0/2,5/5 V | 0/512/1023 |
| PWM 0/64/128/192/255 | duty/frequência do timer e extremos estáveis |
| `Serial.println("ok")` | bytes `6f 6b 0d 0a` |
| TX > ring em 300 baud | espera virtual; tick não bloqueia |
| include absoluto/`../`/symlink | rejeição antes do compilador |
| Wire/SPI/Servo | `UNSUPPORTED_LIBRARY` explícito |
| template/preprocessor bomb | worker morto no limite; nenhum cache parcial |
| ELF mutado/overlap | verificador rejeita estruturalmente |
| fetch de `SPM` ou opcode proibido | runtime falha antes de mutar estado |
| build repetido limpo | `CRLFirmware` byte-identical |
| replay 100 vezes | trace e state hash idênticos |
| resposta worker atrasada | descartada por generation/revision/epoch |
| workers concluem fora de ordem | barrier produz trace global idêntico |
| cliente forja firmware/pins | rejeitado; nenhum efeito no mundo |
| 32 placas saturadas | tick limitado; atraso/indisponibilidade operacional sem mutar trace lógico |
| save no meio de delay/UART | reload continua igual ao trace sem interrupção |
| save entre resposta e barrier | checkpoint/output antigo persiste; quantum é reexecutado |
| schema/checksum hostil | placa desabilitada sem grande alocação |
| worker tenta rede/filesystem/processo | syscall negada em teste de integração |

Fuzzing cobre bundle, preprocessor envelope, protocolo, ELF, `CRLFirmware`, NBT e
respostas VM. Testes de isolamento devem demonstrar negação; configuração apenas
declarativa não basta.

## Clean-room, licenças e marca

- Implementadores do interpretador trabalham a partir desta RFC, datasheet
  público ATmega328P, documentação de instruções AVR e traces black-box.
- Código do Core não é copiado para Java. Core 1.8.6 e shim/patch permanecem
  fontes separadas do worker, com SBOM, notices, digests e source correspondente.
- Distribuição do core/toolchain exige auditoria de LGPL/GPL, runtime exception e
  materiais de relink quando aplicável. Revisão jurídica precede release público.
- “Arduino” é referência de compatibilidade e marca de Arduino S.r.l.; Craftônica
  não implica afiliação/endosso, não usa logo, arte ou silkscreen da placa.
- A RoboBoard terá visual e nome autorais. Documentação inclui: “Arduino é marca
  de Arduino S.r.l. Craftônica é um projeto independente, sem afiliação ou
  endosso da Arduino.”

## Sequência de implementação

1. `CRL-30`: bundle, supervisor isolado, toolchain fixado, verificador e formato
   canônico; ainda sem ativação no mundo.
2. `CRL-31`: interpretador, scheduler, checkpoint e RoboBoard servidor-autoritativa.
3. `CRL-35`: editor/Serial com revision locking e limites de protocolo.
4. `CRL-32`: bridge de sensores/atuadores por snapshots e lotes validados.
5. Gates de segurança: fuzzing, isolamento real, saturação multiplayer e replay.

Nenhuma etapa pode substituir isolamento ausente por confiança no sketch,
timeout na thread ou execução “temporária” de C++ nativo no host.
