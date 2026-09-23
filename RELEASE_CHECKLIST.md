# Release checklist 2.0.0

## Fonte, assets e metadados

- [ ] Worktree limpo e commit de release identificado.
- [ ] `build.gradle`, `Craftonica.VERSION`, `mcmod.info` e README resolvem para `2.0.0`.
- [ ] `CHANGELOG.md`, `LICENSE`, notices e inventário visual presentes.
- [ ] Nenhum asset provisório está classificado como final.
- [ ] Schemas, migração 1.2 e rollback permanecem verificados.

## Gates

```sh
./scripts/gradle-java8.sh clean test build verifyReleaseJar verifyReleaseMetadata
./scripts/gradle-java8.sh verifyMigrationGates electricalProfile firmwareIntegration
./scripts/gradle-java8.sh physicsAudit roboticsSoakProfile roboticsFailureProfile
scripts/firmware/bootstrap-avr-toolchain.sh --verify-only
scripts/firmware/test-compiler.sh
(cd tools/mcp && python3 -m unittest test_craftonica_mcp.py)
git diff --check
./scripts/verify-reproducible-release.sh
```

## Instalação limpa e smoke

- [ ] Extrair o `.tar.gz` e validar o `.sha256` e `SHA256SUMS`.
- [ ] Executar `verify-release-examples.sh` a partir do pacote extraído.
- [ ] Instalar em diretórios vazios de cliente e servidor com `install-release.sh`.
- [ ] Confirmar mod `2.0.0`, mundo novo, migração 1.2 e backup verificado.
- [ ] Construir 2WD, 4WD e braço seguindo o currículo, sem comandos geradores.
- [ ] Executar circuito nominal, Blink, Serial, servos, sensores e multiplayer.
- [ ] Confirmar log sem exceção Craftônica e registrar hashes do mod/worker.

## Publicação

- [ ] Publicar somente o tar reproduzível, SHA-256, commit e tag `v2.0.0`.
- [ ] Publicar requisitos Linux, Java 8, Forge fixado e limitações conhecidas.
- [ ] Anexar changelog, currículo, notices, auditorias e rollback.
- [ ] Baixar a release publicada, validar checksums e instalar novamente antes do anúncio.
