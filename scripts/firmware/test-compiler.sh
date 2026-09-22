#!/usr/bin/env bash
set -euo pipefail
umask 077

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
TEMP_DIR=$(mktemp -d -- "${TMPDIR:-/tmp}/craftonica-compiler-test.XXXXXXXX")
trap 'rm -rf -- "$TEMP_DIR"' EXIT HUP INT TERM
mkdir -p -- "$TEMP_DIR/blink" "$TEMP_DIR/servo" "$TEMP_DIR/out-a" "$TEMP_DIR/out-b" "$TEMP_DIR/out-servo" "$TEMP_DIR/hostile"
UNIT_SEQUENCE=0

run_compile() {
    UNIT_SEQUENCE=$((UNIT_SEQUENCE + 1))
    CRAFTONICA_SYSTEMD_UNIT=$(printf 'craftonica-compile-%032x.service' "$UNIT_SEQUENCE") \
        "$SCRIPT_DIR/compile-sketch.sh" "$@"
}

printf '%s\n' \
    'void setup() {' \
    '  pinMode(LED_BUILTIN, OUTPUT);' \
    '}' \
    'void loop() {' \
    '  digitalWrite(LED_BUILTIN, HIGH);' \
    '  delay(500);' \
    '  digitalWrite(LED_BUILTIN, LOW);' \
    '  delay(500);' \
    '}' > "$TEMP_DIR/blink/Blink.ino"

run_compile "$TEMP_DIR/blink" Blink.ino "$TEMP_DIR/out-a"
run_compile "$TEMP_DIR/blink" Blink.ino "$TEMP_DIR/out-b"
cmp -- "$TEMP_DIR/out-a/firmware.elf" "$TEMP_DIR/out-b/firmware.elf"
cmp -- "$TEMP_DIR/out-a/firmware.hex" "$TEMP_DIR/out-b/firmware.hex"
cmp -- "$TEMP_DIR/out-a/firmware.map" "$TEMP_DIR/out-b/firmware.map"

cat > "$TEMP_DIR/servo/ServoDemo.ino" <<'EOF'
#include <Servo.h>
Servo arm;
void setup() { arm.attach(9); arm.write(90); }
void loop() { arm.writeMicroseconds(1750); delay(20); }
EOF
run_compile "$TEMP_DIR/servo" ServoDemo.ino "$TEMP_DIR/out-servo"
[[ -s "$TEMP_DIR/out-servo/firmware.elf" && -s "$TEMP_DIR/out-servo/firmware.hex" ]]

expect_rejected() {
    local label=$1
    if run_compile "$TEMP_DIR/hostile" Hostile.ino "$TEMP_DIR/out-a" >/dev/null 2>&1; then
        printf 'FAIL: hostile case was accepted: %s\n' "$label" >&2
        exit 1
    fi
    printf 'PASS: rejected %s\n' "$label"
}

printf '#include "../secret.h"\nvoid setup() {}\nvoid loop() {}\n' > "$TEMP_DIR/hostile/Hostile.ino"
expect_rejected traversal
printf '#include </etc/passwd>\nvoid setup() {}\nvoid loop() {}\n' > "$TEMP_DIR/hostile/Hostile.ino"
expect_rejected absolute-include
printf '#include <Wire.h>\nvoid setup() {}\nvoid loop() {}\n' > "$TEMP_DIR/hostile/Hostile.ino"
expect_rejected hostile-include
printf 'void setup() { asm("nop"); }\nvoid loop() {}\n' > "$TEMP_DIR/hostile/Hostile.ino"
expect_rejected inline-assembly
printf '#pragma GCC optimize ("O0")\nvoid setup() {}\nvoid loop() {}\n' > "$TEMP_DIR/hostile/Hostile.ino"
expect_rejected pragma
printf '%%:include </etc/passwd>\nvoid setup() {}\nvoid loop() {}\n' > "$TEMP_DIR/hostile/Hostile.ino"
expect_rejected digraph-include
printf '#define A a\n#define B sm\n#define JOIN(a,b) a ## b\nvoid setup() { JOIN(A,B)("nop"); }\nvoid loop() {}\n' > "$TEMP_DIR/hostile/Hostile.ino"
expect_rejected token-pasted-assembly
printf 'void setup() { a\\\nsm("nop"); }\nvoid loop() {}\n' > "$TEMP_DIR/hostile/Hostile.ino"
expect_rejected spliced-assembly
printf 'void setup() { __asm("nop"); }\nvoid loop() {}\n' > "$TEMP_DIR/hostile/Hostile.ino"
expect_rejected alternate-inline-assembly

SECCOMP="$TEMP_DIR/seccomp.bpf"
"$SCRIPT_DIR/generate-seccomp.py" "$SECCOMP"
if ! systemd-run --user --scope --quiet -p MemoryMax=64M -p TasksMax=8 \
    "$SCRIPT_DIR/sandbox-launch.sh" "$SECCOMP" \
    --unshare-all --unshare-user --disable-userns --die-with-parent --new-session --cap-drop ALL \
    --ro-bind /usr /usr --symlink usr/lib /lib --symlink usr/lib /lib64 \
    /usr/bin/python3 -c 'import errno,socket,sys
try:
    socket.socket()
except OSError as error:
    sys.exit(0 if error.errno == errno.EPERM else 3)
sys.exit(2)' >/dev/null 2>&1; then
    printf 'FAIL: seccomp did not return EPERM for socket syscall\n' >&2
    exit 1
fi
printf 'PASS: seccomp denied socket syscall\n'

printf 'PASS: Blink ELF/HEX/map are byte-identical across two clean builds\n'
printf 'PASS: Servo.h compiles for the published D9/D10 Timer1 profile\n'
