#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="$(sed -n 's/^version = "\([^"]*\)"/\1/p' "$ROOT/build.gradle")"
TEMP="$(mktemp -d -- "${TMPDIR:-/tmp}/craftonica-release.XXXXXXXX")"
trap 'rm -rf -- "$TEMP"' EXIT HUP INT TERM

if [[ ${CRAFTONICA_VERIFY_SKIP_BUILD:-false} == true ]]; then
    CRAFTONICA_SKIP_BUILD=true CRAFTONICA_RELEASE_ROOT="$TEMP/a" "$ROOT/scripts/package-release.sh"
    CRAFTONICA_SKIP_BUILD=true CRAFTONICA_RELEASE_ROOT="$TEMP/b" "$ROOT/scripts/package-release.sh"
else
    CRAFTONICA_RELEASE_ROOT="$TEMP/a" "$ROOT/scripts/package-release.sh"
    CRAFTONICA_RELEASE_ROOT="$TEMP/b" "$ROOT/scripts/package-release.sh"
fi
cmp -- "$TEMP/a/craftonica-$VERSION.tar.gz" "$TEMP/b/craftonica-$VERSION.tar.gz"
cmp -- "$TEMP/a/craftonica-$VERSION.tar.gz.sha256" "$TEMP/b/craftonica-$VERSION.tar.gz.sha256"

mkdir "$TEMP/client" "$TEMP/server"
"$TEMP/a/craftonica-$VERSION/install-release.sh" "$TEMP/client"
"$TEMP/a/craftonica-$VERSION/install-release.sh" "$TEMP/server"
test -s "$TEMP/client/mods/craftonica-$VERSION.jar"
test -s "$TEMP/server/craftonica-runtime/craftonica-$VERSION-runtime-worker.jar"
test -s "$TEMP/client/craftonica-compiler/firmware/compiler-manifest-v1.txt"
test -x "$TEMP/server/craftonica-compiler/scripts/firmware/compile-sketch.sh"
"$TEMP/a/craftonica-$VERSION/verify-release-examples.sh"
echo "PASS: dois pacotes e checksums idênticos; instalações limpas de cliente/servidor verificadas"
