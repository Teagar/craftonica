#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[[ $# -eq 1 ]] || { echo "Uso: $0 <diretorio-minecraft>" >&2; exit 2; }
TARGET=$1
[[ ! -L "$TARGET" ]] || { echo "Diretório Minecraft não pode ser link simbólico" >&2; exit 1; }
shopt -s nullglob
JARS=("$ROOT"/mods/craftonica-*.jar)
WORKERS=("$ROOT"/craftonica-runtime/craftonica-*-runtime-worker.jar)
[[ ${#JARS[@]} -eq 1 && ${#WORKERS[@]} -eq 1 ]] || { echo "Pacote incompleto" >&2; exit 1; }
(cd "$ROOT" && sha256sum -c SHA256SUMS)

install -d -m 0755 "$TARGET/mods" "$TARGET/craftonica-runtime"
rm -f "$TARGET"/mods/craftonica-*.jar "$TARGET"/craftonica-runtime/craftonica-*-runtime-worker.jar
install -m 0644 "${JARS[0]}" "$TARGET/mods/$(basename "${JARS[0]}")"
install -m 0644 "${WORKERS[0]}" "$TARGET/craftonica-runtime/$(basename "${WORKERS[0]}")"
install -m 0755 "$ROOT/craftonica-runtime/launch-worker.sh" "$TARGET/craftonica-runtime/launch-worker.sh"

COMPILER="$TARGET/craftonica-compiler"
[[ ! -L "$COMPILER" ]] || { echo "Diretório do compilador não pode ser link simbólico" >&2; exit 1; }
rm -rf -- "$COMPILER/firmware" "$COMPILER/scripts"
install -d -m 0755 "$COMPILER/firmware" "$COMPILER/scripts/firmware"
cp -R "$ROOT/firmware/." "$COMPILER/firmware/"
cp -R "$ROOT/scripts/firmware/." "$COMPILER/scripts/firmware/"
find "$COMPILER/scripts/firmware" -type f -name '*.sh' -exec chmod 0755 {} +
find "$COMPILER/scripts/firmware" -type f -name '*.py' -exec chmod 0755 {} +
"$COMPILER/scripts/firmware/bootstrap-avr-toolchain.sh"
echo "Craftônica instalada e checksums verificados em $TARGET"
