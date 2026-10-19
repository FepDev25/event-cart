#!/usr/bin/env bash
# Detiene un proceso de EventCart lanzado con start.sh (laboratorio de fallos, solo dev).
# Uso: stop.sh <api|consumer|worker>
set -euo pipefail

profile="${1:?usage: stop.sh <api|consumer|worker>}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
pid_file="$root/.run/$profile.pid"

if [[ ! -f "$pid_file" ]]; then
  echo "[lab] $profile no estaba en marcha"
  exit 0
fi

pid="$(cat "$pid_file")"
kill "$pid" 2>/dev/null || true
for _ in $(seq 1 30); do
  kill -0 "$pid" 2>/dev/null || break
  sleep 0.5
done
if kill -0 "$pid" 2>/dev/null; then
  kill -9 "$pid" 2>/dev/null || true
fi
rm -f "$pid_file"
echo "[lab] $profile detenido (pid $pid)"
