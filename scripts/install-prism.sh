#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTANCE="${CRAFTONICA_PRISM_INSTANCE:-$HOME/.local/share/PrismLauncher/instances/Craftonica 1.7.10}"

"$ROOT/scripts/gradle-java8.sh" clean test build verifyReleaseJar
mkdir -p "$INSTANCE/minecraft/mods"
rm -f "$INSTANCE"/minecraft/mods/craftonica-*.jar
install -m 0644 "$ROOT/build/libs/craftonica-0.1.1.jar" "$INSTANCE/minecraft/mods/craftonica-0.1.1.jar"
sha256sum "$INSTANCE/minecraft/mods/craftonica-0.1.1.jar"
