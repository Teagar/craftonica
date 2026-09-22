#!/usr/bin/env bash
set -euo pipefail
umask 077

die() {
    printf 'compiler: %s\n' "$*" >&2
    exit 1
}

[[ $# -eq 3 ]] || die 'usage: compile-sketch.sh SOURCE_DIR MAIN.ino OUTPUT_DIR'
SOURCE_DIR=$1
MAIN_FILE=$2
OUTPUT_DIR=$3
SYSTEMD_UNIT=${CRAFTONICA_SYSTEMD_UNIT:-}
[[ "$MAIN_FILE" =~ ^[A-Za-z][A-Za-z0-9_-]{0,63}\.ino$ ]] || die 'unsafe main filename'
[[ "$SYSTEMD_UNIT" =~ ^craftonica-compile-[0-9a-f]{32}\.service$ ]] || die 'missing or unsafe compiler unit identity'
[[ -d "$SOURCE_DIR" && ! -L "$SOURCE_DIR" ]] || die 'source directory is unavailable or is a symlink'
[[ -d "$OUTPUT_DIR" && ! -L "$OUTPUT_DIR" ]] || die 'output directory is unavailable or is a symlink'

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
SOURCE_DIR=$(realpath -e -- "$SOURCE_DIR")
OUTPUT_DIR=$(realpath -e -- "$OUTPUT_DIR")
[[ "$SOURCE_DIR" != "$OUTPUT_DIR" ]] || die 'source and output directories must differ'
command -v bwrap >/dev/null 2>&1 || die 'bubblewrap is required'
command -v python3 >/dev/null 2>&1 || die 'python3 is required for validation'
command -v systemd-run >/dev/null 2>&1 || die 'systemd-run is required for cgroup enforcement'
command -v timeout >/dev/null 2>&1 || die 'timeout is required'

INSTALL_DIR=$("$SCRIPT_DIR/bootstrap-avr-toolchain.sh" --verify-only)
[[ -d "$INSTALL_DIR" && ! -L "$INSTALL_DIR" ]] || die 'verified toolchain install is unavailable'
STAGING_DIR=$(mktemp -d -- "${TMPDIR:-/tmp}/craftonica-source.XXXXXXXX")
DIAGNOSTICS=$(mktemp -- "${TMPDIR:-/tmp}/craftonica-diagnostics.XXXXXXXX")
SECCOMP=$(mktemp -- "${TMPDIR:-/tmp}/craftonica-seccomp.XXXXXXXX")
rm -f -- "$SECCOMP"
trap 'rm -rf -- "$STAGING_DIR"; rm -f -- "$DIAGNOSTICS" "$SECCOMP"' EXIT HUP INT TERM
rmdir -- "$STAGING_DIR"
set +e
"$SCRIPT_DIR/prepare_bundle.py" "$SOURCE_DIR" "$MAIN_FILE" "$STAGING_DIR"
prepare_status=$?
set -e
[[ $prepare_status -eq 0 ]] || exit 65
"$SCRIPT_DIR/generate-seccomp.py" "$SECCOMP"

rm -f -- "$OUTPUT_DIR/firmware.elf" "$OUTPUT_DIR/firmware.hex" "$OUTPUT_DIR/firmware.map"
set +e
timeout --signal=TERM --kill-after=0.25s 12s \
    systemd-run --user --wait --collect --pipe --quiet --unit="$SYSTEMD_UNIT" \
        -p MemoryMax=256M -p MemorySwapMax=0 -p TasksMax=32 -p CPUQuota=100% \
        -p RuntimeMaxSec=10s -p TimeoutStopSec=250ms -p KillMode=control-group \
    "$SCRIPT_DIR/sandbox-launch.sh" "$SECCOMP" \
        --unshare-all --unshare-user --disable-userns --die-with-parent --new-session --cap-drop ALL \
        --clearenv --setenv LC_ALL C --setenv LANG C --setenv TZ UTC --setenv SOURCE_DATE_EPOCH 0 \
        --size 33554432 --tmpfs /work --proc /proc --dev /dev \
        --dir /bin --dir /usr --dir /usr/lib --dir /toolchain --dir /core --dir /libraries --dir /source --dir /out --dir /runner \
        --ro-bind /usr/bin/bash /bin/bash \
        --ro-bind /usr/bin/cp /bin/cp \
        --ro-bind /usr/bin/mkdir /bin/mkdir \
        --ro-bind /usr/lib/ld-linux-x86-64.so.2 /usr/lib/ld-linux-x86-64.so.2 \
        --ro-bind /usr/lib/libc.so.6 /usr/lib/libc.so.6 \
        --ro-bind /usr/lib/libm.so.6 /usr/lib/libm.so.6 \
        --ro-bind /usr/lib/libdl.so.2 /usr/lib/libdl.so.2 \
        --ro-bind "$(realpath -e /usr/lib/libreadline.so.8)" /usr/lib/libreadline.so.8 \
        --ro-bind "$(realpath -e /usr/lib/libncursesw.so.6)" /usr/lib/libncursesw.so.6 \
        --ro-bind "$(realpath -e /usr/lib/libacl.so.1)" /usr/lib/libacl.so.1 \
        --ro-bind "$(realpath -e /usr/lib/libattr.so.1)" /usr/lib/libattr.so.1 \
        --symlink usr/lib /lib --symlink usr/lib /lib64 \
        --ro-bind "$INSTALL_DIR/toolchain" /toolchain \
        --ro-bind "$INSTALL_DIR/core" /core \
        --ro-bind "$SCRIPT_DIR/../../firmware/libraries" /libraries \
        --ro-bind "$STAGING_DIR" /source \
        --ro-bind "$SCRIPT_DIR/sandbox-build.sh" /runner/build.sh \
        --ro-bind "$SCRIPT_DIR/../../firmware/abi/craftonica_abi.S" /runner/craftonica_abi.S \
        --bind "$OUTPUT_DIR" /out \
        --chdir /work \
        /bin/bash /runner/build.sh >"$DIAGNOSTICS" 2>&1
status=$?
set -e

if [[ $(stat -c '%s' -- "$DIAGNOSTICS") -gt 16384 ]]; then
    dd if="$DIAGNOSTICS" bs=16384 count=1 status=none >&2
    printf '\ncompiler: diagnostics truncated at 16384 bytes\n' >&2
else
    cat -- "$DIAGNOSTICS" >&2
fi
if [[ $status -ne 0 ]]; then
    rm -f -- "$OUTPUT_DIR/firmware.elf" "$OUTPUT_DIR/firmware.hex" "$OUTPUT_DIR/firmware.map"
    if [[ $status -eq 124 || $status -eq 137 ]]; then
        printf 'compiler: COMPILER_UNAVAILABLE: sandbox wall-time limit exceeded\n' >&2
        exit 70
    fi
    [[ $status -eq 66 || $status -eq 67 ]] && exit "$status"
    printf 'compiler: COMPILER_UNAVAILABLE: sandbox failed with status %s\n' "$status" >&2
    exit 70
fi
[[ -s "$OUTPUT_DIR/firmware.elf" && -s "$OUTPUT_DIR/firmware.hex" && -s "$OUTPUT_DIR/firmware.map" ]] || die 'compiler did not emit every required artifact'
