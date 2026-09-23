# Instalação 2.0

## Requisitos

- Minecraft Java 1.7.10;
- Forge 10.13.4.1614;
- Linux x86_64;
- Java 8;
- `bubblewrap` e `systemd --user` para compilação/runtime AVR.

Baixe `craftonica-2.0.0.tar.gz` e o arquivo `.sha256` da release. Valide antes de
extrair:

```sh
sha256sum -c craftonica-2.0.0.tar.gz.sha256
tar -xzf craftonica-2.0.0.tar.gz
cd craftonica-2.0.0
sha256sum -c SHA256SUMS
```

Instale em uma instância limpa de cliente ou servidor:

```sh
./install-release.sh /caminho/para/minecraft
```

O instalador publica:

```text
mods/craftonica-2.0.0.jar
craftonica-runtime/craftonica-2.0.0-runtime-worker.jar
craftonica-runtime/launch-worker.sh
craftonica-compiler/firmware/
craftonica-compiler/scripts/firmware/
```

O instalador também publica o compilador verificável e prepara o toolchain AVR.
Os exemplos permanecem no pacote. Para validar Blink, Servo e sensores:

```sh
scripts/firmware/bootstrap-avr-toolchain.sh
scripts/firmware/test-compiler.sh
```

## Primeiro início

1. Confirme `Craftonica: Robotics Lab 2.0.0` na lista de mods.
2. Crie um mundo de teste antes de abrir saves importantes.
3. Monte o circuito fonte–botão–220 Ω–LED–GND.
4. Siga `docs/curriculum/2.0-robotica-modular.md` para 2WD, 4WD e braço.
5. Em servidor dedicado, mantenha decisões de física e firmware no servidor.

## Migração e rollback

Leia `docs/migracao-1.2-para-2.0.md` antes de abrir um mundo 1.2. A primeira carga
cria um snapshot verificado. Para downgrade, restaure esse snapshot com
`rollback-world.sh` e abra a cópia somente com o JAR público 1.2.0. Nunca abra o
mundo já escrito pela 2.0 diretamente na versão antiga.
