#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="$(sed -n 's/^version = "\([^"]*\)"/\1/p' "$ROOT/build.gradle")"
[[ -n "$VERSION" ]] || { echo "Versão não encontrada" >&2; exit 1; }

"$ROOT/scripts/gradle-java8.sh" clean test build verifyReleaseJar verifyReleaseMetadata verifyMigrationGates electricalProfile firmwareIntegration

OUT="$ROOT/build/release/craftonica-$VERSION"
mkdir -p "$OUT"
cp "$ROOT/build/libs/craftonica-$VERSION.jar" "$OUT/"
cp "$ROOT/build/runtime/craftonica-$VERSION-runtime-worker.jar" "$OUT/"
cp "$ROOT/scripts/runtime/launch-worker.sh" "$OUT/"
cp "$ROOT/CHANGELOG.md" "$ROOT/LICENSE" "$ROOT/THIRD_PARTY_NOTICES.md" "$OUT/"
cp "$ROOT/docs/installation-1.2.md" "$ROOT/docs/robo-movel-guiado.md" \
   "$ROOT/docs/compatibilidade-1.2.md" "$OUT/"

(cd "$OUT" && find . -maxdepth 1 -type f ! -name SHA256SUMS -printf '%f\n' \
  | LC_ALL=C sort | xargs sha256sum > SHA256SUMS)
echo "Release reproduzível em $OUT"
