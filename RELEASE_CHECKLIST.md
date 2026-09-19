# Release checklist 1.1.0

## Fonte e metadados

- [ ] Worktree limpo e commit de release identificado.
- [ ] `build.gradle`, `Craftonica.VERSION`, `mcmod.info` e README resolvem para `1.1.0`.
- [ ] `CHANGELOG.md`, `LICENSE` e `THIRD_PARTY_NOTICES.md` presentes.
- [ ] Schemas persistentes e manifestos permanecem compativeis/verificados.

## Gates

```sh
./scripts/gradle-java8.sh clean test build verifyReleaseJar verifyReleaseMetadata verifyMigrationGates electricalProfile firmwareIntegration
scripts/firmware/bootstrap-avr-toolchain.sh --verify-only
scripts/firmware/test-compiler.sh
(cd tools/mcp && python3 -m unittest test_craftonica_mcp.py)
git diff --check
```

## Instalacao limpa

- [ ] Instalar em cliente Prism vazio com `scripts/install-prism.sh`.
- [ ] Instalar em servidor Forge vazio com `scripts/install-instance.sh <diretorio>`.
- [ ] Confirmar mod `1.1.0`, mundo novo, mundo migrado e backup verificado.
- [ ] Executar circuito nominal, seis laboratorios, Blink, Serial e multiplayer.
- [ ] Confirmar log sem excecao Craftonica e registrar SHA-256 do mod/worker/manifesto.

## Publicacao

- [ ] Publicar JAR reobfuscado, commit/tag e checksums; nao publicar JAR de desenvolvimento.
- [ ] Publicar requisitos Linux, Java 8, Forge fixado e limitacoes conhecidas.
- [ ] Anexar changelog, guias, notices e procedimento de rollback.
- [ ] Validar os artefatos baixados novamente antes de anunciar a release.
