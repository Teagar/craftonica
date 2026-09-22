# Compilador AVR externo no Linux

O perfil inicial e fixo e corresponde a
`craftonica-avr-uno:1/core-1.8.6/gcc-7.3.0-atmel3.6.1-arduino7`.
Ele usa Arduino avr-gcc `7.3.0-atmel3.6.1-arduino7`, Arduino AVR Boards
`1.8.6`, ATmega328P a 16 MHz e a variante `standard`. LTO nao e usado.

## Preparacao

```sh
scripts/firmware/bootstrap-avr-toolchain.sh
```

O bootstrap baixa os dois arquivos somente por HTTPS para
`~/.cache/craftonica/firmware/archives`, confere tamanho e SHA-256 antes de
extrair e aplica o patch versionado de GPIO. A instalacao fica em
`~/.cache/craftonica/firmware/installed`. Toda execucao volta a validar os
arquivos baixados, o marcador e o digest canonico completo das arvores extraidas.
Arquivo, marcador, conteudo ou layout
inesperado desabilita a compilacao; o compilador AVR instalado no host nunca e
consultado.

O patch troca o retorno silencioso dos entrypoints publicos `pinMode`,
`digitalWrite` e `digitalRead` para pin invalido pela trap ABI normativa:
`ldi r24,1; sts 0xc1f0,r24`. O runtime futuro deve transformar essa escrita em
`INVALID_PIN` antes de qualquer alteracao de CPU/MMIO.

## Invocacao

```sh
mkdir -p /tmp/craftonica-result
scripts/firmware/compile-sketch.sh /caminho/bundle Blink.ino /tmp/craftonica-result
```

O bundle e plano, contem no maximo 16 arquivos e 64 KiB, com no maximo 32 KiB
por arquivo. Sao aceitos `.ino`, `.h`, `.c` e `.cpp`; o nome principal deve ser
ASCII seguro e terminar em `.ino`. O resultado contem `firmware.elf`,
`firmware.hex` e `firmware.map`. O arquivo principal vem primeiro e outros
`.ino` seguem ordem bytewise. O preprocessor insere `Arduino.h`, `#line` e
prototipos de funcoes globais simples quando necessario.

Includes locais so podem nomear um `.h` no proprio bundle. A allowlist inicial
de headers de sistema contem Arduino/core, C/avr-libc, `Servo.h` e os prefixos
`avr/` e `util/`. Bibliotecas como Wire e SPI falham como nao suportadas. Includes
absolutos, travessia, symlinks, assembly inline, `_Pragma`, `#pragma`, `#line`,
`#include_next` e includes calculados sao rejeitados antes do compilador.

## Isolamento fail-closed

`compile-sketch.sh` exige bubblewrap e uma instalacao verificada. O job usa novos
namespaces de usuario, PID, mount, IPC, UTS, cgroup e rede (`--unshare-all`), sem
capabilities, com `no_new_privs` aplicado pelo bubblewrap, rede ausente,
workspace `tmpfs`, toolchain/core/source somente leitura e ambiente limpo. Nao
ha bind de home, saves, diretorio do jogo, repositorio, agentes SSH ou socket de
containers. Somente o diretorio de saida explicitamente fornecido e gravavel.
Um filtro seccomp cBPF x86_64 nega syscalls de rede, novos namespaces/mounts,
`ptrace`, BPF, perf, keyring e interfaces de modulo/kernel; o gate executa uma
tentativa real de `socket()` e exige `EPERM`.

O job exige um user manager systemd e cria cgroup v2 descartavel com 256 MiB de
memoria, swap zero, uma CPU e 32 tarefas. Os limites internos sao CPU 5 s, wall
10 s com 250 ms para encerramento, address space 512 MiB, arquivo 2 MiB, 32
processos, 64 FDs, core dump zero e diagnostico 16
KiB. Locale, timezone e `SOURCE_DATE_EPOCH` sao fixos. Os argumentos de
compilacao/link sao arrays fixos; nao ha `eval`, response file ou interpretacao
de fonte pelo shell. `avr-gcc`, `avr-g++`, `avr-ar`, linker, objcopy e size vem
sempre de `/toolchain/bin` dentro do sandbox.

`Servo.h` é uma biblioteca confiável incluída pelo perfil. Ela oferece `attach`,
`detach`, `write`, `writeMicroseconds`, `read`, `readMicroseconds` e `attached`
somente em D9/D10. Usa Timer1 em fast PWM, prescaler 8, TOP 39999 (50 Hz) e aceita
pulsos de 1000–2000 microssegundos. Pino ocupado, faixa inválida ou outro pino
retorna `INVALID_SERVO`; não há fallback por software ou interrupção oculta.

O supervisor Java revalida ELF32/AVR hostil, extrai apenas segmentos `PT_LOAD`
dentro de flash/SRAM e emite `CRLFirmware` canonico. Cache de compilacao inclui
perfil, digests das arvores fixadas, sketch principal e hash exato das fontes;
somente firmware revalidado entra no cache imutavel.

Este supervisor e o gate local de CRL-30. Implantacao publica ainda exige UID de
servico dedicado e microVM. Na falta
de qualquer controle exigido nesse ambiente, compilacao deve permanecer
desabilitada, nunca cair para execucao sem sandbox.

## Teste

```sh
scripts/firmware/test-compiler.sh
```

O teste compila Blink duas vezes e compara ELF, HEX e mapa byte a byte. Tambem
exige rejeicao previa de include com travessia, include absoluto, biblioteca
nao permitida, assembly inline e pragma.
