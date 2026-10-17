# Modelo de dominio — EventCart (M1)

Diseño del núcleo de inventario, reservas y pedidos. Es la referencia para el esquema
(`V1__core_schema.sql`), las entidades y la máquina de estados. **PostgreSQL es la fuente de verdad**;
Kafka (M3) solo transportará hechos.

## Reconocimiento (bounded contexts)

| Contexto | Responsabilidad | Entidades |
|----------|-----------------|-----------|
| `launch` | Publicar/programar la venta y **contabilizar el stock** | `Launch` |
| `reservation` | Captura atómica de stock, expiración, estados | `Reservation` |
| `checkout` | Pago simulado y resultado | (servicio) |
| `order` | Pedido confirmado y sus datos históricos | `Order` |
| `identity` (M2) | Comprador/organizador y sesiones | `User` |
| `eventing` (M3) | Outbox, contratos de eventos, consumidores | `Outbox` |

## Value objects

- **Money**: `amount numeric(12,2)` + `currency char(3)` ISO-4217. `BigDecimal` en Java.
- **LaunchStatus**: `DRAFT → SCHEDULED → OPEN → CLOSED` (+ `CANCELLED` en cualquier punto anterior a terminal).
- **ReservationStatus**: `RESERVED → CONFIRMED | RELEASED | EXPIRED`.
- **OrderStatus**: `PENDING → CONFIRMED | FAILED`.

## Agregados

### Launch (raíz del contador de inventario)

| Campo | Tipo | Notas |
|-------|------|-------|
| `id` | uuid | PK, generado en la app |
| `title`, `description` | text | |
| `product_name`, `product_ref` | text | **producto embebido** (sin tabla `product`) |
| `unit_price`, `currency` | numeric(12,2), char(3) | precio de referencia |
| `total_stock` | integer ≥ 0 | unidades del lanzamiento |
| `available_units`, `reserved_units`, `confirmed_units` | integer ≥ 0 | **contadores contables** |
| `status` | text + CHECK | ciclo de vida |
| `opens_at`, `closes_at` | timestamptz | autorización de venta en servidor (UTC) |
| `reservation_ttl_seconds` | integer > 0 | default 600 (10 min) |
| `per_buyer_limit` | integer \| null | null = sin límite |
| `seller_id` | uuid | organizador (FK a `users` en M2) |
| `version` | bigint | bloqueo optimista |
| `created_at`, `updated_at` | timestamptz | |

**Invariante (CHECK en BD):**

```
available_units + reserved_units + confirmed_units = total_stock
```

Todos los contadores son `≥ 0`. Cualquier valor negativo o desbalance viola la constraint y la transacción
entera falla: el mecanismo de no-sobreventa tiene una red de seguridad a nivel de BD.

**Congelado al `OPEN`:** una vez `OPEN`, `unit_price`, `total_stock` y `opens_at/closes_at` no se editan
(regla de aplicación + tests; los importes ya quedan *snapshot* en reserva/pedido).

### Reservation

| Campo | Tipo | Notas |
|-------|------|-------|
| `id` | uuid | PK |
| `launch_id` | uuid | FK |
| `buyer_id` | uuid | (FK a `users` en M2) |
| `quantity` | integer > 0 | **una fila por reserva con cantidad** |
| `unit_price`, `currency`, `total_amount` | numeric | *snapshot* del lanzamiento al reservar |
| `status` | text + CHECK | `RESERVED/CONFIRMED/RELEASED/EXPIRED` |
| `expires_at` | timestamptz | TTL persistente |
| `version`, `created_at`, `updated_at` | | |

CHECK: `total_amount = quantity * unit_price`.

### Order

| Campo | Tipo | Notas |
|-------|------|-------|
| `id` | uuid | PK |
| `reservation_id` | uuid | FK **UNIQUE** (1:1 con la reserva) |
| `launch_id`, `buyer_id` | uuid | FK |
| `quantity`, `unit_price`, `currency`, `total_amount` | | *snapshot* comercial (histórico inmutable) |
| `status` | text + CHECK | `PENDING/CONFIRMED/FAILED` |
| `payment_result` | text | `APPROVED/DECLINED/TIMEOUT` (simulación) |
| `failure_reason` | text | motivo de fallo |
| `version`, `created_at`, `updated_at` | | |

CHECK: `total_amount = quantity * unit_price`.

### BuyerLaunchUsage

`(buyer_id, launch_id)` PK · `reserved_units`, `confirmed_units` · `updated_at`.
Soporta el **límite por comprador** de forma atómica (enforcement en M2): `reserved + confirmed ≤ per_buyer_limit`.

### IdempotencyKeys

`UNIQUE (scope, idempotency_key)` · `request_hash` · `resource_id` · `response_*` · `expires_at`.
`scope` ej.: `reservation:create`, `checkout`. Mismo resultado ante reintento; rechazo si el `request_hash` difiere.

## Máquina de estados de la reserva

```
                 ┌──────────────┐
   reservar ───▶ │   RESERVED   │
                 └──────┬───────┘
        checkout OK  ───┼────────▶ CONFIRMED   (terminal)
        cancelar     ───┼────────▶ RELEASED    (terminal)
        TTL vencido  ───┴────────▶ EXPIRED     (terminal)
```

- Sin transiciones desde estados terminales.
- `RESERVED → CONFIRMED`: reserva y descuento de stock ya ocurrieron al crear; confirmar solo mueve
  `reserved_units → confirmed_units` en el mismo lanzamiento.
- `RESERVED → RELEASED` (cancelación explícita) y `→ EXPIRED` (TTL): `reserved_units → available_units`.
- Liberación **exactamente una vez** incluso bajo carrera con confirmación (M4), garantizada por un
  `UPDATE ... WHERE status = 'RESERVED'` condicional (0 filas ⇒ ya transicionó).

## Captura atómica de stock (no-sobreventa)

Una sola sentencia condicional, sin locks explícitos ni esperas largas:

```sql
UPDATE launches
SET available_units = available_units - :qty,
    reserved_units  = reserved_units  + :qty,
    updated_at      = now()
WHERE id = :launchId
  AND status = 'OPEN'
  AND opens_at <= now() AND now() < closes_at
  AND available_units >= :qty;

-- 1 fila afectada  → reserva creada
-- 0 filas          → rechazo (sin stock / fuera de ventana / no OPEN)
```

Mantiene el invariante (el CHECK lo respalda) y es resistente a reintentos porque la condición de
stock se reevalúa en la propia escritura.

## Expiración y checkout pendiente

- `expires_at` persistente; un job periódico (no depende de Kafka) expira `RESERVED` vencidas y libera stock.
- El **ciclo de checkout vive en `Order`** (`PENDING` = resultado indeterminado). La reserva permanece
  `RESERVED`; la ventana de gracia y la reconciliación de carreras checkout/expiración se afinan en M2/M4.

## Fuera de este documento (hitos posteriores)

- Identidad real (`users`, sesiones) y FK de `seller_id`/`buyer_id` → **M2**.
- Endpoints REST, idempotencia aplicada, límite por comprador → **M2**.
- Outbox, eventos versionados, Kafka, consumidores → **M3**.
- Job de expiración, reconciliación e inyección de fallos → **M4**.
