#!/bin/sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
jdk_version=jdk8u504-b01
jdk_root=${CRAFTONICA_JDK_CACHE:-"$HOME/.cache/craftonica"}
jdk_home="$jdk_root/$jdk_version"
archive="$jdk_root/OpenJDK8U-jdk_x64_linux_hotspot_8u504b01.tar.gz"
url=https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u504-b01/OpenJDK8U-jdk_x64_linux_hotspot_8u504b01.tar.gz
checksum=9c70e102f527ac674ac2fe9c7d47b9a04e2d19842ba5ab8e9b33f368bbadfaea

if [ "$(uname -s)" != Linux ] || [ "$(uname -m)" != x86_64 ]; then
  printf '%s\n' "Automatic Java 8 setup supports Linux x86_64 only. Set JAVA_HOME to a JDK 8 installation." >&2
  exit 1
fi

if [ ! -x "$jdk_home/bin/java" ]; then
  mkdir -p "$jdk_root"
  if [ ! -f "$archive" ]; then
    curl -fL "$url" -o "$archive"
  fi
  printf '%s  %s\n' "$checksum" "$archive" | sha256sum -c -
  tar -xzf "$archive" -C "$jdk_root"
fi

cd "$project_dir"
JAVA_HOME="$jdk_home" PATH="$jdk_home/bin:$PATH" ./gradlew "$@"
