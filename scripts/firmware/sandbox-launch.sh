#!/usr/bin/env bash
set -euo pipefail

[[ $# -gt 1 && -f "$1" && ! -L "$1" ]] || exit 70
filter=$1
shift
exec 9<"$filter"
exec bwrap --seccomp 9 "$@"
