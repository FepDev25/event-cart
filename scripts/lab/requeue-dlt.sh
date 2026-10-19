#!/usr/bin/env bash
# Reproceso MANUAL y controlado: reinyecta la DLT en su tema original. Solo laboratorio/dev.
# Es seguro porque los consumidores deduplican por event_id (at-least-once idempotente).
# Uso: requeue-dlt.sh <eventcart.reservation|eventcart.order> [max]
set -euo pipefail

topic="${1:?usage: requeue-dlt.sh <eventcart.reservation|eventcart.order> [max]}"
max="${2:-1000}"

echo "[lab] reinyectando ${topic}.DLT → ${topic} (máx ${max}). Dedup por event_id hace el reproceso seguro."
docker exec -i eventcart-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic "${topic}.DLT" \
  --from-beginning \
  --max-messages "$max" \
  --timeout-ms 10000 \
| docker exec -i eventcart-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic "$topic"
