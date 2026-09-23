#!/usr/bin/env bash
set -euo pipefail

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
[[ $# -eq 1 || $# -eq 2 ]] || {
  printf 'Uso: %s <diretorio-backup> [save-vazio-ou-ausente]\n' "$0" >&2
  exit 2
}
BACKUP=$1
TARGET=${2:-}
[[ ! -L "$BACKUP" && ( -z "$TARGET" || ! -L "$TARGET" ) ]] || {
  printf 'Links simbolicos nao sao aceitos.\n' >&2
  exit 1
}
shopt -s nullglob
JARS=("$ROOT"/build/libs/craftonica-*.jar)
[[ ${#JARS[@]} -eq 1 ]] || {
  printf 'Compile primeiro; esperado exatamente um build/libs/craftonica-*.jar.\n' >&2
  exit 1
}
JAVA_BIN=${JAVA_HOME:+$JAVA_HOME/bin/}java
if [[ -z "$TARGET" ]]; then
  exec "$JAVA_BIN" -cp "${JARS[0]}" br.com.craftonica.persistence.WorldBackupCli verify "$BACKUP"
fi
exec "$JAVA_BIN" -cp "${JARS[0]}" br.com.craftonica.persistence.WorldBackupCli restore "$BACKUP" "$TARGET"
