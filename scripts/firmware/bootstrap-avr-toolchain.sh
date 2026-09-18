#!/usr/bin/env bash
set -euo pipefail
umask 077

PROFILE='craftonica-avr-uno-1-core-1.8.6-gcc-7.3.0-atmel3.6.1-arduino7'
TOOLCHAIN_URL='https://downloads.arduino.cc/tools/avr-gcc-7.3.0-atmel3.6.1-arduino7-x86_64-pc-linux-gnu.tar.bz2'
TOOLCHAIN_SHA256='bd8c37f6952a2130ac9ee32c53f6a660feb79bee8353c8e289eb60fdcefed91e'
TOOLCHAIN_SIZE='37630618'
CORE_URL='https://downloads.arduino.cc/cores/staging/avr-1.8.6.tar.bz2'
CORE_SHA256='ff1d17274b5a952f172074bd36c3924336baefded0232e10982f8999c2f7c3b6'
CORE_SIZE='7127080'
TOOLCHAIN_TREE_SHA256='dcf1200210b85a7270ead0497f9c79a3bc973636f801e68d7de40289ca4ddd81'
CORE_TREE_SHA256='6cf1485643317311afbc5abf4714d11f0e25a0899c4bd1da94943e1b2eab31ba'

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd -P)
PATCH_FILE="$ROOT_DIR/firmware/patches/arduino-avr-core-1.8.6-invalid-pin-trap.patch"
CACHE_ROOT=${CRAFTONICA_FIRMWARE_CACHE:-"${XDG_CACHE_HOME:-$HOME/.cache}/craftonica/firmware"}
ARCHIVE_DIR="$CACHE_ROOT/archives"
INSTALL_DIR="$CACHE_ROOT/installed/$PROFILE"
TOOLCHAIN_ARCHIVE="$ARCHIVE_DIR/avr-gcc-7.3.0-atmel3.6.1-arduino7.tar.bz2"
CORE_ARCHIVE="$ARCHIVE_DIR/avr-1.8.6.tar.bz2"

die() {
    printf 'bootstrap: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "required command is unavailable: $1"
}

verify_file() {
    local path=$1 expected_size=$2 expected_sha=$3 actual_size actual_sha
    [[ -f "$path" && ! -L "$path" ]] || return 1
    actual_size=$(stat -c '%s' -- "$path")
    [[ "$actual_size" == "$expected_size" ]] || return 1
    actual_sha=$(sha256sum -- "$path")
    actual_sha=${actual_sha%% *}
    [[ "$actual_sha" == "$expected_sha" ]]
}

fetch_archive() {
    local path=$1 url=$2 size=$3 sha=$4 temporary
    if verify_file "$path" "$size" "$sha"; then
        return
    fi
    rm -f -- "$path"
    temporary="$path.part.$$"
    rm -f -- "$temporary"
    curl --fail --location --proto '=https' --tlsv1.2 --retry 3 --output "$temporary" "$url"
    verify_file "$temporary" "$size" "$sha" || {
        rm -f -- "$temporary"
        die "download failed size/SHA-256 verification: $url"
    }
    mv -- "$temporary" "$path"
}

marker_text() {
    printf '%s\n' \
        'format=craftonica-firmware-toolchain-marker-1' \
        "profile=$PROFILE" \
        "toolchain_size=$TOOLCHAIN_SIZE" \
        "toolchain_sha256=$TOOLCHAIN_SHA256" \
        "core_size=$CORE_SIZE" \
        "core_sha256=$CORE_SHA256" \
        "core_patch_sha256=$PATCH_SHA256"
}

verify_install() {
    local marker_sha expected_marker_sha toolchain_tree_sha core_tree_sha
    [[ -d "$INSTALL_DIR/toolchain" && ! -L "$INSTALL_DIR/toolchain" ]] || return 1
    [[ -d "$INSTALL_DIR/core" && ! -L "$INSTALL_DIR/core" ]] || return 1
    [[ -x "$INSTALL_DIR/toolchain/bin/avr-g++" ]] || return 1
    [[ -f "$INSTALL_DIR/core/cores/arduino/Arduino.h" ]] || return 1
    [[ -f "$INSTALL_DIR/core/variants/standard/pins_arduino.h" ]] || return 1
    [[ -f "$INSTALL_DIR/.verified" && ! -L "$INSTALL_DIR/.verified" ]] || return 1
    marker_sha=$(sha256sum -- "$INSTALL_DIR/.verified")
    marker_sha=${marker_sha%% *}
    expected_marker_sha=$(marker_text | sha256sum)
    expected_marker_sha=${expected_marker_sha%% *}
    [[ "$marker_sha" == "$expected_marker_sha" ]] || return 1
    toolchain_tree_sha=$(tar --sort=name --mtime='@0' --owner=0 --group=0 --numeric-owner -C "$INSTALL_DIR/toolchain" -cf - . | sha256sum)
    toolchain_tree_sha=${toolchain_tree_sha%% *}
    core_tree_sha=$(tar --sort=name --mtime='@0' --owner=0 --group=0 --numeric-owner -C "$INSTALL_DIR/core" -cf - . | sha256sum)
    core_tree_sha=${core_tree_sha%% *}
    [[ "$toolchain_tree_sha" == "$TOOLCHAIN_TREE_SHA256" && "$core_tree_sha" == "$CORE_TREE_SHA256" ]]
}

require_command curl
require_command patch
require_command sha256sum
require_command stat
require_command tar
require_command flock
[[ -f "$PATCH_FILE" && ! -L "$PATCH_FILE" ]] || die 'core safety patch is missing or is a symlink'
PATCH_SHA256=$(sha256sum -- "$PATCH_FILE")
PATCH_SHA256=${PATCH_SHA256%% *}
VERIFY_ONLY=false
if [[ ${1:-} == '--verify-only' ]]; then
    VERIFY_ONLY=true
elif [[ $# -ne 0 ]]; then
    die 'usage: bootstrap-avr-toolchain.sh [--verify-only]'
fi

mkdir -p -- "$ARCHIVE_DIR" "$CACHE_ROOT/installed"
[[ ! -L "$CACHE_ROOT" && ! -L "$ARCHIVE_DIR" && ! -L "$CACHE_ROOT/installed" ]] || die 'cache directories must not be symlinks'
exec 8>"$CACHE_ROOT/.bootstrap.lock"
flock 8

if verify_install; then
    printf '%s\n' "$INSTALL_DIR"
    exit 0
fi

if [[ "$VERIFY_ONLY" == true ]]; then
    die "verified install is unavailable: run $0"
fi
fetch_archive "$TOOLCHAIN_ARCHIVE" "$TOOLCHAIN_URL" "$TOOLCHAIN_SIZE" "$TOOLCHAIN_SHA256"
fetch_archive "$CORE_ARCHIVE" "$CORE_URL" "$CORE_SIZE" "$CORE_SHA256"

TEMP_DIR=$(mktemp -d -- "$CACHE_ROOT/install.XXXXXXXX")
trap 'rm -rf -- "$TEMP_DIR"' EXIT HUP INT TERM
mkdir -- "$TEMP_DIR/toolchain" "$TEMP_DIR/core"
tar -xjf "$TOOLCHAIN_ARCHIVE" --strip-components=1 -C "$TEMP_DIR/toolchain"
tar -xjf "$CORE_ARCHIVE" --strip-components=1 -C "$TEMP_DIR/core"
patch --batch --forward --directory="$TEMP_DIR/core" -p1 < "$PATCH_FILE"

[[ -x "$TEMP_DIR/toolchain/bin/avr-g++" ]] || die 'toolchain archive has an unexpected layout'
[[ -f "$TEMP_DIR/core/cores/arduino/Arduino.h" ]] || die 'core archive has an unexpected layout'
[[ -f "$TEMP_DIR/core/variants/standard/pins_arduino.h" ]] || die 'variant is missing'
marker_text > "$TEMP_DIR/.verified"

[[ ! -e "$INSTALL_DIR" && ! -L "$INSTALL_DIR" ]] || die 'invalid immutable install exists; remove it explicitly before reinstalling'
mv -- "$TEMP_DIR" "$INSTALL_DIR"
trap - EXIT HUP INT TERM
verify_install || die 'new install marker verification failed'
printf '%s\n' "$INSTALL_DIR"
