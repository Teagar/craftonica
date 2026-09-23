#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -d "$SCRIPT_DIR/examples" ]]; then ROOT="$SCRIPT_DIR"; else ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"; fi
TMP="$(mktemp -d -- "${TMPDIR:-/tmp}/craftonica-examples.XXXXXXXX")"
trap 'rm -rf -- "$TMP"' EXIT HUP INT TERM

"$ROOT/scripts/firmware/test-compiler.sh"
n=0
while IFS= read -r sketch; do
    n=$((n + 1)); mkdir "$TMP/out-$n"
    CRAFTONICA_SYSTEMD_UNIT=$(printf 'craftonica-compile-%032x.service' "$n") \
        "$ROOT/scripts/firmware/compile-sketch.sh" "$(dirname "$sketch")" \
        "$(basename "$sketch")" "$TMP/out-$n" >/dev/null
    test -s "$TMP/out-$n/firmware.hex"
    echo "PASS: $(basename "$(dirname "$sketch")")"
done < <(find "$ROOT/examples/arduino" -name '*.ino' -print | LC_ALL=C sort)
