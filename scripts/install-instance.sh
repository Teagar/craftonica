#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ $# -eq 1 ]] || { echo "Uso: $0 <diretorio-minecraft>" >&2; exit 2; }
MINECRAFT_DIR=$1
[[ ! -L "$MINECRAFT_DIR" ]] || { echo "Diretorio Minecraft nao pode ser link simbolico" >&2; exit 1; }

"$ROOT/scripts/gradle-java8.sh" clean test build verifyReleaseJar
shopt -s nullglob
JARS=("$ROOT"/build/libs/craftonica-*.jar)
WORKERS=("$ROOT"/build/runtime/craftonica-*-runtime-worker.jar)
[[ ${#JARS[@]} -eq 1 ]] || { echo "Esperado um JAR de distribuicao, encontrados: ${#JARS[@]}" >&2; exit 1; }
[[ ${#WORKERS[@]} -eq 1 ]] || { echo "Esperado um runtime worker, encontrados: ${#WORKERS[@]}" >&2; exit 1; }

install -d -m 0755 "$MINECRAFT_DIR/mods"
rm -f "$MINECRAFT_DIR"/mods/craftonica-*.jar
install -m 0644 "${JARS[0]}" "$MINECRAFT_DIR/mods/$(basename "${JARS[0]}")"

RUNTIME_DIR="$MINECRAFT_DIR/craftonica-runtime"
install -d -m 0755 "$RUNTIME_DIR"
rm -f "$RUNTIME_DIR"/craftonica-*-runtime-worker.jar
install -m 0644 "${WORKERS[0]}" "$RUNTIME_DIR/$(basename "${WORKERS[0]}")"
install -m 0755 "$ROOT/scripts/runtime/launch-worker.sh" "$RUNTIME_DIR/launch-worker.sh"

COMPILER_DIR="$MINECRAFT_DIR/craftonica-compiler"
[[ ! -L "$COMPILER_DIR" ]] || { echo "Diretorio do compilador nao pode ser link simbolico" >&2; exit 1; }
install -d -m 0755 "$COMPILER_DIR/scripts/firmware" "$COMPILER_DIR/firmware/abi" "$COMPILER_DIR/firmware/patches"
install -d -m 0700 "$COMPILER_DIR/state" "$COMPILER_DIR/state/work" "$COMPILER_DIR/state/cache"
install -m 0644 "$ROOT/firmware/compiler-manifest-v1.txt" "$COMPILER_DIR/firmware/compiler-manifest-v1.txt"
install -m 0644 "$ROOT/firmware/abi/craftonica_abi.S" "$COMPILER_DIR/firmware/abi/craftonica_abi.S"
install -m 0644 "$ROOT/firmware/patches/arduino-avr-core-1.8.6-invalid-pin-trap.patch" \
    "$COMPILER_DIR/firmware/patches/arduino-avr-core-1.8.6-invalid-pin-trap.patch"
for file in bootstrap-avr-toolchain.sh compile-sketch.sh generate-seccomp.py prepare_bundle.py sandbox-build.sh sandbox-launch.sh; do
    install -m 0755 "$ROOT/scripts/firmware/$file" "$COMPILER_DIR/scripts/firmware/$file"
done
"$COMPILER_DIR/scripts/firmware/bootstrap-avr-toolchain.sh"

sha256sum "$MINECRAFT_DIR/mods/$(basename "${JARS[0]}")"
sha256sum "$RUNTIME_DIR/$(basename "${WORKERS[0]}")"
sha256sum "$COMPILER_DIR/firmware/compiler-manifest-v1.txt"
