# Diseño de Outbox + Kafka — EventCart (M3)

Emisión de eventos de negocio **realmente comprometidos** mediante **Transactional Outbox** y su
procesamiento con consumidores idempotentes. Postgres sigue siendo la fuente de verdad; Kafka
transporta hechos derivados. Semántica declarada: **at-least-once**, sin prometer exactly-once
end-to-end.

## 1. Alcance de M3

Emitir `ReservationCreated`, `ReservationReleased`, `OrderConfirmed`, `PaymentSimulationFailed` vía
outbox; publicarlos a Kafka; y procesarlos con 3 consumidores con efectos observables, deduplicación,
reintentos con backoff y DLT.

No incluye: expiración/reconciliación (M4), frontend (M6), reproceso con UI (M7).

## 2. Estructura de paquetes (`com.eventcart.eventing`)

```
eventing/
  contract/        EventEnvelope, EventType, Topics, payloads de eventos
  outbox/          OutboxEntity, OutboxJpaRepository, OutboxWriter, OutboxPublisher
  consumer/        NotificationsConsumer, SalesProjectionConsumer, AuditConsumer, ProcessedEventGuard
  projection/      SalesProjectionEntity, AuditLogEntity, NotificationEntity (+ repos)
  config/          KafkaTopicsConfig, KafkaErrorHandlingConfig, EventingProperties
```

## 3. Contrato de eventos

**Envelope** JSON autocontenido (value del mensaje):

```json
{
  "eventId": "9f1c...",
  "type": "ReservationCreated",
  "version": 1,
  "aggregateId": "b3a1...",
  "occurredAt": "2026-01-01T10:00:00Z",
  "payload": { "...": "..." }
}
```

### Catálogo M3

| Tipo | Agregado (`aggregateId`) | Topic | Payload (campos) |
|------|--------------------------|-------|------------------|
| `ReservationCreated` | reservationId | `eventcart.reservation` | reservationId, launchId, buyerId, quantity, unitPrice, currency, totalAmount, expiresAt |
| `ReservationReleased` | reservationId | `eventcart.reservation` | reservationId, launchId, buyerId, quantity |
| `OrderConfirmed` | orderId | `eventcart.order` | orderId, reservationId, launchId, buyerId, quantity, totalAmount, currency |
| `PaymentSimulationFailed` | orderId | `eventcart.order` | orderId, reservationId, launchId, buyerId, quantity, reason |

- `version` empieza en 1; un cambio incompatible sube la versión y el consumidor decide.
- Datos mínimos (sin PII más allá de ids); sin tarjetas ni secretos.
- Serialización: **String (JSON+Jackson 3)**, parseo manual para controlar tipos/versiones desconocidos.

## 4. Topics y particionado

| Topic | Clave | Particiones (local) |
|-------|-------|---------------------|
| `eventcart.reservation` | `aggregateId` (reservationId) | 3 |
| `eventcart.order` | `aggregateId` (orderId) | 3 |
| `eventcart.<topic>.DLT` | (misma clave) | 3 |

- Clave = `aggregateId` ⇒ orden **por agregado**; no se asume orden global.
- Topics creados por `NewTopic` beans (KafkaAdmin) con replication-factor 1 en local.

## 5. Esquema de base de datos — migración `V3__eventing.sql`

```sql
CREATE TABLE outbox (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id uuid NOT NULL,
    event_type   text NOT NULL,
    payload      jsonb NOT NULL,          -- envelope completo
    occurred_at  timestamptz NOT NULL,
    published_at timestamptz,
    attempts     integer NOT NULL DEFAULT 0,
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    consumer     text NOT NULL,
    event_id     uuid NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);

CREATE TABLE sales_projection (
    launch_id       uuid PRIMARY KEY,
    confirmed_units bigint NOT NULL DEFAULT 0,
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    id           bigserial PRIMARY KEY,
    event_id     uuid NOT NULL,
    event_type   text NOT NULL,
    aggregate_id uuid NOT NULL,
    occurred_at  timestamptz NOT NULL,
    recorded_at  timestamptz NOT NULL DEFAULT now(),
    payload      jsonb NOT NULL
);

CREATE TABLE notifications (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    buyer_id   uuid,
    type       text NOT NULL,
    message    text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
```

## 6. Producción: outbox en la misma transacción

`OutboxWriter.append(aggregateId, eventType, payload)` construye el envelope (eventId = UUID,
version = 1, occurredAt = `Clock`) y **inserta la fila `outbox` dentro de la transacción de negocio**
existente. Se integra en:

- `ReservationApplicationService.reserve` → `ReservationCreated`
- `ReservationApplicationService.cancel` → `ReservationReleased`
- `CheckoutService.applyResult` (fase C): `APPROVED` → `OrderConfirmed`; `DECLINED` → `PaymentSimulationFailed`

Así, el evento solo existe si el cambio de estado se comprometió. Si la tx revierte, no hay evento.

## 7. Publicador (`OutboxPublisher`)

`@Scheduled(fixedDelay = eventcart.outbox.poll-interval)`:

```
seleccionar lote de filas sin publicar (ORDER BY created_at LIMIT batch-size)
para cada fila (en orden, por agregado):
    topic = Topics.forEventType(type)     # eventcart.reservation | eventcart.order
    kafkaTemplate.send(topic, aggregate_id, payload).get()   # síncrono: orden + confirmación
    marcar published_at = now()
ante fallo: incrementar attempts, dejar sin publicar (reintenta en el siguiente ciclo) y log WARN
```

- **Recuperación tras reinicio:** las filas `published_at IS NULL` se reintentan.
- Acepta **entregas duplicadas** (si falla el marcado tras publicar) ⇒ consumidores idempotentes.
- Un solo hilo de scheduler ⇒ sin solapamiento.

## 8. Consumidores (3 grupos)

`@KafkaListener` con `StringDeserializer`; el cuerpo se ejecuta en `@Transactional`:

| Consumidor | groupId | Topics | Efecto observable | Eventos |
|-----------|---------|--------|-------------------|---------|
| Notifications | `eventcart-notifications` | reservation, order | inserta fila en `notifications` (mensaje ficticio por comprador) | Created / Confirmed / Failed / Released |
| Sales Projection | `eventcart-sales` | order | `sales_projection.confirmed_units += qty` (upsert) | OrderConfirmed |
| Audit/Operations | `eventcart-audit` | reservation, order | append a `audit_log` | todos |

**Deduplicación** (`ProcessedEventGuard`): por cada evento, primero
`INSERT INTO processed_events(consumer, event_id) ON CONFLICT DO NOTHING`; si inserta 0 filas ⇒ ya
procesado ⇒ se ignora. La inserción y el efecto ocurren en **la misma transacción de BD**, de modo
que un reintento tras fallo reprocesa (la fila de dedup revierte).

**Ack:** `AckMode.RECORD` (commit tras aplicar el efecto). Reintento si el método lanza.

## 9. Reintentos, DLT y eventos problemáticos

- `DefaultErrorHandler` con **backoff** (reintentos limitados) + `DeadLetterPublishingRecoverer` que
  publica a `<topic>.DLT` al agotar intentos.
- `UnsupportedEventException`: **tipo desconocido** o **versión no soportada** ⇒ se lanza para que el
  handler lo derive a `.DLT` (circuito de investigación explícito), sin bloquear la partición.
- El reproceso desde `.DLT` es **manual/controlado** (M7), nunca ciego.

## 10. Observabilidad mínima

- Logs estructurados por evento publicado/consumido/descartado con `eventId`, `type`, `aggregateId`.
- Micrometer/Actuator: contadores de publicados, fallos del publicador, duplicados ignorados,
  eventos enviados a DLT.
- Sin herramientas de observabilidad empresarial (se usan las existentes).

## 11. Configuración

```yaml
spring:
  kafka:
    bootstrap-servers: ${EVENTCART_KAFKA_BOOTSTRAP:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
    listener:
      ack-mode: record
eventcart:
  outbox:
    poll-interval: 1s
    batch-size: 100
```

## 12. Docker Compose (Kafka KRaft + UI)

- Broker Apache Kafka en modo **KRaft** (sin Zookeeper), listener en `localhost:9092`.
- **Kafka UI** para inspección de topics/mensajes/grupos.
- El `bootstrap-servers` por defecto apunta a `localhost:9092` (configurable con `EVENTCART_KAFKA_BOOTSTRAP`).

## 13. Estrategia de pruebas

- **Testcontainers Kafka** (broker real) añadido a la config compartida de test:
  - **Outbox → publicación**: cambio de estado confirmado aparece en el topic en un instante acotado.
  - **Reinicio del publicador**: filas pendientes se publican sin duplicar efectos visibles.
  - **Deduplicación/redelivery**: publicar el mismo evento dos veces ⇒ efecto aplicado una sola vez.
  - **DLT**: evento con tipo/versión desconocida termina en `<topic>.DLT`.
- Los tests actuales de Postgres siguen pasando (mismo contexto con Kafka arrancado).

## 14. Fuera de alcance (recordatorio)

- `ReservationExpired` y reconciliación de `PENDING` → **M4**.
- Reproceso con UI, k6 y métricas de lag → **M7**.
- No se declara exactly-once end-to-end.
