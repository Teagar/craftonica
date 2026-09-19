#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTANCE="${CRAFTONICA_PRISM_INSTANCE:-$HOME/.local/share/PrismLauncher/instances/Craftonica 1.7.10}"
exec "$ROOT/scripts/install-instance.sh" "$INSTANCE/minecraft"
