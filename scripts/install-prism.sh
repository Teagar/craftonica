#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTANCE="${CRAFTONICA_PRISM_INSTANCE:-$HOME/.local/share/PrismLauncher/instances/Craftonica 1.7.10}"

"$ROOT/scripts/gradle-java8.sh" clean test build verifyReleaseJar
shopt -s nullglob
JARS=("$ROOT"/build/libs/craftonica-*.jar)
if [[ ${#JARS[@]} -ne 1 ]]; then
    echo "Esperado exatamente um JAR de distribuição, encontrados: ${#JARS[@]}" >&2
    exit 1
fi
mkdir -p "$INSTANCE/minecraft/mods"
rm -f "$INSTANCE"/minecraft/mods/craftonica-*.jar
install -m 0644 "${JARS[0]}" "$INSTANCE/minecraft/mods/$(basename "${JARS[0]}")"
RUNTIME_DIR="$INSTANCE/minecraft/craftonica-runtime"
WORKERS=("$ROOT"/build/runtime/craftonica-*-runtime-worker.jar)
if [[ ${#WORKERS[@]} -ne 1 ]]; then
    echo "Esperado exatamente um runtime worker, encontrados: ${#WORKERS[@]}" >&2
    exit 1
fi
WORKER_JAR="${WORKERS[0]}"
mkdir -p "$RUNTIME_DIR"
rm -f "$RUNTIME_DIR"/craftonica-*-runtime-worker.jar
install -m 0644 "$WORKER_JAR" "$RUNTIME_DIR/$(basename "$WORKER_JAR")"
install -m 0755 "$ROOT/scripts/runtime/launch-worker.sh" "$RUNTIME_DIR/launch-worker.sh"
COMPILER_DIR="$INSTANCE/minecraft/craftonica-compiler"
[[ ! -L "$COMPILER_DIR" ]] || { echo "Diretório do compilador não pode ser link simbólico" >&2; exit 1; }
install -d -m 0755 "$COMPILER_DIR/scripts/firmware" "$COMPILER_DIR/firmware/abi" "$COMPILER_DIR/firmware/patches"
install -d -m 0700 "$COMPILER_DIR/state" "$COMPILER_DIR/state/work" "$COMPILER_DIR/state/cache"
install -m 0644 "$ROOT/firmware/compiler-manifest-v1.txt" "$COMPILER_DIR/firmware/compiler-manifest-v1.txt"
install -m 0644 "$ROOT/firmware/abi/craftonica_abi.S" "$COMPILER_DIR/firmware/abi/craftonica_abi.S"
install -m 0644 "$ROOT/firmware/patches/arduino-avr-core-1.8.6-invalid-pin-trap.patch" \
    "$COMPILER_DIR/firmware/patches/arduino-avr-core-1.8.6-invalid-pin-trap.patch"
for compiler_file in bootstrap-avr-toolchain.sh compile-sketch.sh generate-seccomp.py prepare_bundle.py sandbox-build.sh sandbox-launch.sh; do
    install -m 0755 "$ROOT/scripts/firmware/$compiler_file" "$COMPILER_DIR/scripts/firmware/$compiler_file"
done
"$COMPILER_DIR/scripts/firmware/bootstrap-avr-toolchain.sh"
sha256sum "$INSTANCE/minecraft/mods/$(basename "${JARS[0]}")"
sha256sum "$RUNTIME_DIR/$(basename "$WORKER_JAR")"
sha256sum "$COMPILER_DIR/firmware/compiler-manifest-v1.txt"
