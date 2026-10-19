#!/usr/bin/env bash
# Detiene el broker Kafka (Postgres sigue arriba). Solo laboratorio/dev.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
docker compose -f "$root/docker-compose.yml" stop kafka
echo "[lab] kafka detenido. La API debe seguir aceptando reservas vía outbox."
