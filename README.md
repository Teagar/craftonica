# Craftonica: Robotics Lab

Mod educacional para Minecraft 1.7.10 e Forge 10.13.4.1614. O mundo funciona como uma bancada onde circuitos eletricos sao montados com blocos, sem usar a potencia da Redstone como modelo eletrico.

## Requisitos

- Linux x86_64 com `curl`, `sha256sum` e `tar`; ou
- JDK 8 local para executar `./gradlew` diretamente.

## Build

```sh
./scripts/gradle-java8.sh clean build
```

O script baixa uma distribuicao Temurin 8 fixada e verifica seu SHA-256 em
`~/.cache/craftonica`. Ele nao altera o Java padrao do sistema.

O artefato e gerado em `build/libs/craftonica-0.1.0.jar`.

## Desenvolvimento

Com JDK 8 local:

```sh
./gradlew setupDecompWorkspace
./gradlew runClient
./gradlew runServer
```

Se o host legado de assets da Mojang estiver indisponivel, o servidor dedicado
pode ser iniciado com `./scripts/gradle-java8.sh runServer -PskipAssets`. Um
cache existente de launcher pode ser usado pelo cliente com
`-PskipAssets -PcraftonicaAssetDir=/caminho/para/assets`.

O uso de Java posterior ao 8 nao e suportado pelo ForgeGradle 1.2.
