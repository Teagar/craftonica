#!/usr/bin/env bash
set -euo pipefail

[[ $# -eq 2 ]] || exit 64
java_home=$(readlink -f -- "$1")
worker_jar=$(readlink -f -- "$2")
[[ -x "$java_home/bin/java" && -f "$worker_jar" && ! -L "$worker_jar" ]] || exit 65

ulimit -S -f 0
ulimit -S -n 32
ulimit -S -c 0

binds=(--dir /runtime --ro-bind "$java_home" /runtime/java --ro-bind "$worker_jar" /runtime/worker.jar)
for path in /lib /lib64 /usr/lib; do
    [[ ! -e "$path" ]] || binds+=(--ro-bind "$path" "$path")
done

exec systemd-run --user --scope --quiet -p MemoryMax=256M -p MemorySwapMax=0 -p TasksMax=32 -p CPUQuota=100% \
  bwrap --unshare-all --new-session --die-with-parent \
    "${binds[@]}" \
    --proc /proc --dev /dev --tmpfs /tmp --tmpfs /home --dir /home/worker \
    --setenv HOME /home/worker --setenv TMPDIR /tmp \
    --setenv LANG C --setenv LC_ALL C --setenv TZ UTC \
    /runtime/java/bin/java -Xms8m -Xmx64m -XX:+UseSerialGC -jar /runtime/worker.jar
