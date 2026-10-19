#!/usr/bin/env bash
# Arranca un proceso de EventCart en segundo plano (laboratorio de fallos, solo dev).
# Uso: start.sh <api|consumer|worker>
set -euo pipefail

profile="${1:?usage: start.sh <api|consumer|worker>}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
run_dir="$root/.run"
mkdir -p "$run_dir"

pid_file="$run_dir/$profile.pid"
if [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
  echo "[lab] $profile ya está en marcha (pid $(cat "$pid_file"))"
  exit 0
fi

jar="$(ls "$root"/target/eventcart-*.jar 2>/dev/null | head -1 || true)"
if [[ -z "$jar" ]]; then
  echo "[lab] no hay jar en target/. Ejecuta: ./mvnw -DskipTests package" >&2
  exit 1
fi

: "${JAVA_HOME:=/usr/lib/jvm/java-25-openjdk}"
nohup "$JAVA_HOME/bin/java" -jar "$jar" --spring.profiles.active="$profile" \
  >"$run_dir/$profile.log" 2>&1 &
echo $! >"$pid_file"
echo "[lab] $profile arrancado (pid $!) → $run_dir/$profile.log"
