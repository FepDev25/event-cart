#!/usr/bin/env bash
# Vuelca los mensajes de la DLT de un tema para inspección. Solo laboratorio/dev.
# Uso: inspect-dlt.sh <eventcart.reservation|eventcart.order>
set -euo pipefail

topic="${1:?usage: inspect-dlt.sh <eventcart.reservation|eventcart.order>}"
docker exec eventcart-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic "${topic}.DLT" \
  --from-beginning \
  --property print.key=true \
  --timeout-ms 5000 || true
