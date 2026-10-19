# Failure Lab — EventCart (solo desarrollo)

Guía reproducible para provocar fallos y comprobar que los invariantes del dominio se mantienen.
**Nada de esto se expone en un despliegue público**: los scripts viven en `scripts/lab/` y las
inyecciones usan configuración local o paradas de contenedores propios.

## Topología de procesos (mismo jar, perfiles distintos)

| Perfil              | Web | Outbox publisher | Consumidores | Expiración + reconciliación |
|---------------------|-----|------------------|--------------|-----------------------------|
| por defecto (tests) | sí  | sí               | sí           | no                          |
| `api`               | sí  | sí               | **no**       | no                          |
| `consumer`          | no  | **no**           | sí           | no                          |
| `worker`            | no  | **no**           | **no**       | sí                          |

En el laboratorio se corre un proceso por perfil para poder detener/reiniciar cada uno de forma
aislada.

```bash
./mvnw -DskipTests package          # genera target/eventcart-*.jar
scripts/lab/start.sh api            # o consumer / worker
scripts/lab/stop.sh  consumer
scripts/lab/restart.sh consumer
docker compose up -d                # postgres + kafka + kafka-ui
```

Los PID y logs quedan en `.run/` (ignorado por git).

## Observabilidad mínima

- Métricas: `GET /actuator/metrics/<nombre>` (perfil `api`).
  - `eventcart.outbox.published`, `eventcart.outbox.publish.failures`
  - `eventcart.events.duplicates` (tag `consumer`), `eventcart.events.dlt`
  - `eventcart.reservations.expired`
  - `eventcart.reconciliation.orders_failed`, `eventcart.reconciliation.stale_reserved`
- Kafka UI: <http://localhost:8095> (topics `eventcart.reservation`, `eventcart.order` y sus `.DLT`).

## Experimentos

### 1. Reinicio de consumidor recupera pendientes sin duplicar efectos
1. `scripts/lab/start.sh api` y `start.sh consumer`.
2. `scripts/lab/stop.sh consumer`.
3. Genera reservas/pedidos vía API (el API sigue funcionando; los eventos se acumulan en Kafka).
4. `scripts/lab/start.sh consumer`.
- **Observable:** el consumidor reanuda desde sus offsets comprometidos; `eventcart.events.dlt` no
  crece. Si se reinyectan los mismos eventos, `eventcart.events.duplicates` aumenta y **no** se
  repiten efectos (dedup por `event_id`).

### 2. Caída de Kafka / reinicio del publicador (outbox)
1. `scripts/lab/start.sh api`.
2. `scripts/lab/kafka-stop.sh`.
3. Crea reservas: la API responde y registra filas en `outbox` (no hay publicación inmediata).
4. `scripts/lab/kafka-start.sh`.
- **Observable:** `eventcart.outbox.published` aumenta y las filas pendientes se drenan sin pérdida;
  los eventos llegan a los topics después del reinicio.

### 3. Duplicados y redelivery
- `scripts/lab/inspect-dlt.sh eventcart.reservation` / `eventcart.order` para volcar la DLT.
- Reinyecta manualmente con `scripts/lab/requeue-dlt.sh eventcart.order`.
- **Observable:** reproceso **seguro** porque la dedup es persistente por `event_id`; los efectos
  (proyección de ventas, notificación, auditoría) no se duplican.

### 4. Fallo irrecuperable → DLQ y reproceso controlado
- Envía un evento inválido/veneno al topic; el error handler reintenta con backoff (500 ms × 3) y
  publica en `<topic>.DLT`.
- **Observable:** `eventcart.events.dlt` crece; el evento aparece en la `.DLT`. Se reprocesa a mano
  con `requeue-dlt.sh` tras corregir la causa.

### 5. Checkout lento / rechazado / indeterminado
Configura latencia y usa tokens simulados:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=api \
  -Dspring-boot.run.arguments=--eventcart.checkout.latency=3s,--eventcart.checkout.timeout=1s
```
- `paymentToken=DECLINE` → `Order FAILED`, stock liberado.
- `paymentToken=TIMEOUT` → `Order PENDING` (202); la reconciliación del `worker` lo resuelve a
  `FAILED` cuando supera `eventcart.reconciliation.pending-timeout` (marca `orders_failed`).

### 6. Expiración y carrera con el checkout
- Reserva con `reservationTtlSeconds` corto y **no** completes el checkout.
- **Observable:** el `worker` expira (`eventcart.reservations.expired`) y libera el stock **una sola
  vez**; `eventcart.reconciliation.stale_reserved` vuelve a 0.
- Con checkout lento: la **ventana de gracia** (`eventcart.expiry.grace-window`) protege la reserva
  mientras el `Order` está `PENDING`; si un `APPROVED` llega tras la expiración, el `Order` queda
  `FAILED` **sin** re-liberar stock (la liberación ya ocurrió una vez).
  Cubierto por `CheckoutExpiryRaceIT` y `ReservationExpiryGraceIT`.

## Invariantes que deben seguir siendo verdaderos

1. `disponibles + reservadas activas + confirmadas = stock total` en todo momento; nunca negativo.
2. Cero sobreventa bajo concurrencia; reservas idempotentes (una por clave).
3. Una reserva caducada se libera **exactamente una vez**, incluso en carrera con la confirmación.
4. Un pago aprobado nunca confirma una reserva ya expirada (el `Order` queda `FAILED`).
5. Reinicio de consumidor/publicador recupera lo pendiente sin duplicar efectos (at-least-once +
   dedup por `event_id`).
6. Un `Order PENDING` con resultado indeterminado no queda colgado: la reconciliación lo resuelve.

## Limitaciones

- Broker único (sin réplicas); las métricas de laboratorio no son un benchmark de producción.
- Kafka da garantías **at-least-once**; los efectos son idempotentes, no "exactly-once end-to-end".
- Las proyecciones asíncronas pueden ir retrasadas respecto a PostgreSQL (fuente de verdad del stock).
- Todo lo anterior es de **desarrollo**: no se habilita en despliegues públicos.
