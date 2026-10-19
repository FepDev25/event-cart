#!/usr/bin/env bash
# Rearranca el broker Kafka. Solo laboratorio/dev.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
docker compose -f "$root/docker-compose.yml" start kafka
echo "[lab] kafka arrancado. El publicador de outbox drenará lo pendiente."
