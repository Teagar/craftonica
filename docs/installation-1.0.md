# Instalacao 1.0

## Requisitos comuns

- Linux x86_64;
- Minecraft Java 1.7.10 e Forge 10.13.4.1614;
- `bash`, `curl`, `tar`, `patch`, `flock` e `sha256sum`;
- `bubblewrap` e `systemd --user` para compilacao e runtime AVR;
- acesso HTTPS a `launcher.mojang.com`, `piston-meta.mojang.com`,
  `maven.minecraftforge.net`, `adoptium.net` e `downloads.arduino.cc` no primeiro build.

O build usa Temurin 8 fixado e verificado sem alterar o Java padrao. Nao use Java
9 ou posterior com ForgeGradle 1.2.

## Cliente Prism limpo

1. Crie uma instancia Minecraft `1.7.10` com Forge `10.13.4.1614`.
2. Feche a instancia.
3. No checkout da tag/commit da release, execute:

```sh
CRAFTONICA_PRISM_INSTANCE="/caminho/da/instancia" ./scripts/install-prism.sh
```

Sem a variavel, o destino padrao e
`~/.local/share/PrismLauncher/instances/Craftonica 1.7.10`.

## Servidor dedicado limpo

Instale Forge 10.13.4.1614, aceite a EULA do Minecraft e, com o servidor parado,
execute apontando para o diretorio que contem `mods` e o JAR do servidor:

```sh
./scripts/install-instance.sh /srv/minecraft
```

O instalador faz build limpo, valida o JAR reobfuscado e instala somente:

```text
mods/craftonica-1.0.0.jar
craftonica-runtime/craftonica-1.0.0-runtime-worker.jar
craftonica-runtime/launch-worker.sh
craftonica-compiler/
```

Execute o servidor com o diretorio Minecraft como working directory. O mod
descobre os artefatos acima sem flags adicionais. Clientes multiplayer precisam
da mesma versao do JAR; o compilador e o worker executam somente no servidor.

## Confirmacao

1. Confira `Craftonica: Robotics Lab 1.0.0` na lista de mods.
2. Abra ou crie um mundo; a aba criativa `Craftonica` deve existir.
3. Monte o circuito do [guia do aluno](student-guide.md).
4. Abra uma RoboBoard, compile `examples/arduino/Blink.ino` e confirme D13/Serial.
5. Em servidor dedicado, confirme que o log nao informa
   `COMPILER_ARTIFACTS_MISSING` nem `RUNTIME_ARTIFACTS_MISSING`.

Mundos existentes devem seguir o procedimento de backup e rollback descrito no
[README](../README.md#migracao-e-rollback-de-mundos).
