#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="$(sed -n 's/^version = "\([^"]*\)"/\1/p' "$ROOT/build.gradle")"
[[ -n "$VERSION" ]] || { echo "Versão não encontrada" >&2; exit 1; }

if [[ ${CRAFTONICA_SKIP_BUILD:-false} != true ]]; then
    "$ROOT/scripts/gradle-java8.sh" clean test build verifyReleaseJar verifyReleaseMetadata \
        verifyMigrationGates electricalProfile firmwareIntegration physicsAudit \
        roboticsSoakProfile roboticsFailureProfile
fi

RELEASE_ROOT="${CRAFTONICA_RELEASE_ROOT:-$ROOT/build/release}"
OUT="$RELEASE_ROOT/craftonica-$VERSION"
ARCHIVE="$RELEASE_ROOT/craftonica-$VERSION.tar.gz"
rm -rf -- "$OUT" "$ARCHIVE" "$ARCHIVE.sha256"
mkdir -p "$OUT/mods" "$OUT/craftonica-runtime" "$OUT/docs" "$OUT/examples" \
    "$OUT/scripts/firmware" "$OUT/firmware"

install -m 0644 "$ROOT/build/libs/craftonica-$VERSION.jar" "$OUT/mods/"
install -m 0644 "$ROOT/build/runtime/craftonica-$VERSION-runtime-worker.jar" "$OUT/craftonica-runtime/"
install -m 0755 "$ROOT/scripts/runtime/launch-worker.sh" "$OUT/craftonica-runtime/"
cp -R "$ROOT/docs/." "$OUT/docs/"
cp -R "$ROOT/examples/." "$OUT/examples/"
cp -R "$ROOT/firmware/." "$OUT/firmware/"
for file in bootstrap-avr-toolchain.sh compile-sketch.sh generate-seccomp.py prepare_bundle.py \
        sandbox-build.sh sandbox-launch.sh test-compiler.sh; do
    install -m 0755 "$ROOT/scripts/firmware/$file" "$OUT/scripts/firmware/$file"
done
install -m 0755 "$ROOT/scripts/install-release.sh" "$ROOT/scripts/rollback-world.sh" "$OUT/"
install -m 0755 "$ROOT/scripts/verify-release-examples.sh" "$OUT/"
install -m 0644 "$ROOT/README.md" "$ROOT/CHANGELOG.md" "$ROOT/LICENSE" \
    "$ROOT/THIRD_PARTY_NOTICES.md" "$ROOT/RELEASE_CHECKLIST.md" "$OUT/"

cat > "$OUT/PACKAGE-CONTENTS.txt" <<EOF
Craftonica: Robotics Lab $VERSION
Minecraft 1.7.10 | Forge 10.13.4.1614 | Java 8 | Linux x86_64
mods/: JAR reobfuscado e normalizado
craftonica-runtime/: worker AVR e launcher isolado
firmware/ e scripts/firmware/: compilador verificável e bibliotecas públicas
docs/: instalação, currículo, migração, validações e auditorias
examples/: sketches Arduino-compatible públicos
install-release.sh: instalação limpa de cliente ou servidor
rollback-world.sh: restauração transacional do snapshot pré-migração
EOF

(cd "$OUT" && find . -type f ! -name SHA256SUMS -printf '%P\n' \
  | LC_ALL=C sort | while IFS= read -r file; do sha256sum -- "$file"; done > SHA256SUMS)

tar --sort=name --mtime='@0' --owner=0 --group=0 --numeric-owner \
    -C "$RELEASE_ROOT" -cf - "craftonica-$VERSION" | gzip -n -9 > "$ARCHIVE"
(cd "$RELEASE_ROOT" && sha256sum "$(basename "$ARCHIVE")" > "$(basename "$ARCHIVE").sha256")
echo "Release reproduzível em $ARCHIVE"
