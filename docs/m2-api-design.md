# Diseño de API y backend — EventCart (M2)

Contrato REST, modelo de persistencia y flujos del primer vertical **sin Kafka**: crear/publicar
lanzamiento → reservar atómicamente → checkout simulado → confirmar o liberar. La fuente de verdad
sigue siendo PostgreSQL; la no-sobreventa se garantiza con el `UPDATE` condicional.

## 1. Alcance de M2

Incluye: identidad mínima + sesión, endpoints de organizador y comprador, reserva atómica con
idempotencia y límite por comprador, checkout simulado (con estado `PENDING`), mapeo de errores,
rate limit básico y OpenAPI.

No incluye: Kafka/outbox (M3), job de expiración y reconciliación (M4), frontend (M6).

## 2. Estructura de paquetes (por contexto)

```
com.eventcart
  shared/                 Money, excepciones de dominio, Clock, utilidades
  identity/
    domain/               User, Session, Role (entidades simples)
    persistence/          UserEntity, SessionEntity, repositorios JPA
    service/              AuthService (register/login/validate)
    web/                  AuthController + DTOs
  launch/
    domain/               Launch, LaunchStatus (ya existe)
    persistence/          LaunchEntity, LaunchJpaRepository, LaunchMapper
    service/              LaunchService (crear/editar/transiciones + lectura)
    web/                  LaunchController + DTOs
  reservation/
    domain/               Reservation, ReservationService (ya existen)
    persistence/          ReservationEntity, BuyerLaunchUsageEntity, repos, mapper
    service/              ReservationApplicationService (tx de reserva)
    web/                  ReservationController + DTOs
  checkout/
    service/              CheckoutService, PaymentSimulator
    web/                  CheckoutController + DTOs
  order/
    domain/               Order, OrderStatus (ya existen)
    persistence/          OrderEntity, OrderJpaRepository, mapper
    web/                  OrderController + DTOs
  config/                 SecurityConfig, OpenApiConfig, RateLimitFilter, ClockConfig
  web/                    GlobalExceptionHandler (ProblemDetail)
```

Convención: el **dominio** permanece puro (ya escrito). Cada contexto añade `persistence`, `service`
y `web`. Los servicios de aplicación (`@Service`) orquestan transacciones; los controladores solo
traducen HTTP ↔ DTO.

## 3. Persistencia

### 3.1 Entidades JPA separadas del dominio

El dominio no lleva anotaciones JPA. Se crean entidades de persistencia con campos simples:

| Dominio | Entidad JPA | Notas |
|---------|-------------|-------|
| `Launch` | `LaunchEntity` | `unit_price numeric(12,2)`, `currency`, contadores `available/reserved/confirmed`, `@Version` |
| `Reservation` | `ReservationEntity` | snapshot `unit_price/currency/total_amount`, `status`, `expires_at` |
| `Order` | `OrderEntity` | `reservation_id` `@OneToOne`/`@JoinColumn unique`, `status`, `payment_result` |
| — | `BuyerLaunchUsageEntity` | PK compuesta `(buyerId, launchId)` |
| — | `IdempotencyKeyEntity` | `@UniqueConstraint(scope, idempotency_key)` |
| `identity.User` | `UserEntity` | `email` unique, `password_hash`, `role` |
| `identity.Session` | `SessionEntity` | `token_hash` unique, `expires_at`, `revoked_at` |

Los enums se mapean con `@Enumerated(EnumType.STRING)`.

### 3.2 Mappers (MapStruct)

- **DTO ↔ dominio**: MapStruct (`LaunchWebMapper`, etc.).
- **dominio → entidad**: MapStruct (la entidad tiene setters).
- **entidad → dominio** (rehidratación): requiere preservar `id`, contadores, `status` y `version`.
  Se añaden factories `reconstitute(...)` a los agregados (`Launch.reconstitute`,
  `Reservation.reconstitute`, `Order.reconstitute`) y el mapper las usa (método `default`/`@ObjectFactory`).

> Nota: el dominio actual se construye con factories que generan `id`; para persistencia se añade una
> variante que recibe todos los campos, incluido `id`. `version` se mantiene solo en la entidad JPA.

### 3.3 Repositorios

- Spring Data JPA (`JpaRepository<Entity, UUID>`) para CRUD y lecturas.
- **Ruta crítica de reserva** con SQL nativo `@Modifying @Query` (ver §5).
- `LaunchJpaRepository` expone `decrementAvailableAndReserveForSale(...)` y similares.

### 3.4 Migración `V2__identity.sql`

Añade identidad y cierra las FKs pendientes:

```sql
CREATE TABLE users (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email         text NOT NULL UNIQUE,
    password_hash text NOT NULL,
    display_name  text,
    role          text NOT NULL CHECK (role IN ('BUYER','ORGANIZER')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    version       bigint NOT NULL DEFAULT 0
);

CREATE TABLE sessions (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL REFERENCES users (id),
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_sessions_user    ON sessions (user_id);
CREATE INDEX idx_sessions_expires ON sessions (expires_at);

ALTER TABLE launches     ADD CONSTRAINT fk_launches_seller    FOREIGN KEY (seller_id) REFERENCES users (id);
ALTER TABLE reservations ADD CONSTRAINT fk_reservations_buyer FOREIGN KEY (buyer_id)  REFERENCES users (id);
ALTER TABLE orders       ADD CONSTRAINT fk_orders_buyer       FOREIGN KEY (buyer_id)  REFERENCES users (id);
```

> El volumen de dev contiene filas de verificación de M1; para aplicar las FKs hay que resetear con
> `docker compose down -v` (o crearlas `NOT VALID`). En tests siempre arranca limpio (Testcontainers).

## 4. Contrato de API (`/api/v1`)

Autenticación `Authorization: Bearer <token>`. Errores en `application/problem+json`.

### 4.1 Identidad

| Método | Ruta | Rol | Cuerpo → Respuesta |
|--------|------|-----|--------------------|
| POST | `/auth/register` | público | `{email, password, displayName, role}` → `201 {userId, email, role}` |
| POST | `/auth/session` | público | `{email, password}` → `200 {token, expiresAt, user:{id,email,role}}` |
| DELETE | `/auth/session` | autenticado | revoca el token actual → `204` |
| GET | `/auth/me` | autenticado | → `200 {id, email, role}` |

### 4.2 Lanzamientos

| Método | Ruta | Rol | Notas |
|--------|------|-----|-------|
| POST | `/launches` | ORGANIZER | crea en `DRAFT` |
| PATCH | `/launches/{id}` | ORGANIZER | `reconfigure`; prohibido si `OPEN`/terminal → 409 |
| POST | `/launches/{id}/schedule` | ORGANIZER | `DRAFT → SCHEDULED` |
| POST | `/launches/{id}/open` | ORGANIZER | `DRAFT\|SCHEDULED → OPEN` (publicar) |
| POST | `/launches/{id}/close` | ORGANIZER | `OPEN → CLOSED` |
| POST | `/launches/{id}/cancel` | ORGANIZER | `→ CANCELLED` |
| GET | `/launches/{id}` | público | incluye stock informativo y estado |
| GET | `/launches` | público | lanzamientos publicados/abiertos |

`LaunchResponse`: `{id, title, description, productName, productRef, unitPrice, currency, totalStock,
availableUnits, status, opensAt, closesAt, perBuyerLimit}`.
`CreateLaunchRequest`: `{title, description, productName, productRef, unitPrice, currency, totalStock,
opensAt, closesAt, reservationTtlSeconds, perBuyerLimit}`.

### 4.3 Reservas

| Método | Ruta | Rol | Notas |
|--------|------|-----|-------|
| POST | `/launches/{id}/reservations` | BUYER | header `Idempotency-Key`; body `{quantity}` → `201` o `409` |
| GET | `/reservations/{id}` | dueño | estado + `expiresAt` |
| POST | `/reservations/{id}/cancel` | dueño | `RESERVED → RELEASED` (libera stock) |
| GET | `/reservations` | dueño | lista del comprador |

`ReservationResponse`: `{id, launchId, quantity, unitPrice, totalAmount, status, expiresAt, createdAt}`.

### 4.4 Checkout y pedidos

| Método | Ruta | Rol | Notas |
|--------|------|-----|-------|
| POST | `/reservations/{id}/checkout` | dueño | header `Idempotency-Key`; body `{paymentToken}` |
| GET | `/orders/{id}` | dueño | detalle del pedido |
| GET | `/orders` | dueño | lista del comprador |

Resultado del checkout:
- `200 {order.status: CONFIRMED}` — pago aprobado.
- `200 {order.status: FAILED, failureReason}` — pago rechazado (stock liberado).
- `202 {order.status: PENDING}` — resultado indeterminado (timeout); conciliación posterior (M4).

`CheckoutRequest`: `{paymentToken: "APPROVE" | "DECLINE" | "TIMEOUT"}`.
`OrderResponse`: `{id, reservationId, status, quantity, unitPrice, totalAmount, paymentResult, failureReason}`.

## 5. Flujo transaccional de reserva (no-sobreventa)

Todo en **una transacción** PostgreSQL:

```
1. Insertar/validar clave de idempotencia (scope='reservation:create')      [§6]
2. (si hay límite) upsert atómico en buyer_launch_usage                     [§7]
3. UPDATE condicional de stock:
     UPDATE launches
     SET available_units = available_units - :q,
         reserved_units  = reserved_units  + :q,
         updated_at = now()
     WHERE id = :launchId AND status = 'OPEN'
       AND opens_at <= now() AND now() < closes_at
       AND available_units >= :q;
   -- 0 filas ⇒ conflicto (sin stock / fuera de ventana / no OPEN) → 409
4. INSERT reservations (RESERVED, expires_at = now + reservation_ttl)
5. Registrar respuesta en idempotency_keys
COMMIT
```

Cualquier fallo (paso 2 o 3 con 0 filas) revoca la transacción: no se crea la reserva ni se mueve stock.
El `CHECK chk_launches_stock_sum` actúa como red de seguridad.

## 6. Idempotencia

Alcance `reservation:create` y `checkout`. Flujo:

```
1. Calcular request_hash = SHA-256(cuerpo canónico)
2. Intentar INSERT en idempotency_keys(scope, key, request_hash, expires_at=now+24h)
   - éxito  → continuar con la operación
   - ON CONFLICT (scope,key):
       · request_hash igual   → devolver la respuesta guardada (misma que la 1ª vez)
       · request_hash distinto → 409 Conflict
3. Al terminar, guardar resource_id + response_status + response_body en la fila
```

`Idempotency-Key` ausente en reserva/checkout → `400`.

## 7. Límite por comprador (atómico)

Solo si `launches.per_buyer_limit` no es nulo:

```sql
INSERT INTO buyer_launch_usage (buyer_id, launch_id, reserved_units, confirmed_units)
VALUES (:buyer, :launch, :q, 0)
ON CONFLICT (buyer_id, launch_id) DO UPDATE
SET reserved_units = buyer_launch_usage.reserved_units + :q, updated_at = now()
WHERE buyer_launch_usage.reserved_units + buyer_launch_usage.confirmed_units + :q <= :limit
RETURNING reserved_units;
-- 0 filas ⇒ límite excedido → 409
```

Confirmar mueve `reserved → confirmed`; liberar/expirar reduce `reserved` en la misma tabla.

## 8. Checkout simulado

`PaymentSimulator` (interfaz) con implementación configurable. El token ficticio decide el resultado:

| Token | Simulación | Resultado de dominio |
|-------|-----------|----------------------|
| `APPROVE` | responde dentro del timeout | `Order CONFIRMED` + reserva `CONFIRMED` (stock `reserved → confirmed`) |
| `DECLINE` | responde rechazo | `Order FAILED` + reserva `RELEASED` (stock `reserved → available`) |
| `TIMEOUT` | excede `checkout.timeout` | `Order PENDING` (202); reserva sigue `RESERVED`; conciliación en M4 |

Flujo:

```
1. Idempotencia (scope='checkout')                                         [§6]
2. Cargar reserva del comprador; exigir RESERVED y no expirada → si no, 409
3. Crear Order PENDING
4. Llamar al PaymentSimulator con timeout (no dentro de un lock de BD)
5. Según resultado:
     APPROVED → tx: reservation.confirm + launch.confirmUnits + order.confirm
     DECLINED → tx: order.fail + reservation.release + launch.releaseUnits
     TIMEOUT  → dejar Order PENDING; devolver 202
6. Registrar respuesta idempotente
```

**Ventana de gracia:** el job de expiración (M4) **no** expira reservas que tienen un `Order` en
`PENDING`. Así un checkout lento no pierde el stock por TTL. (Se documenta aquí; se implementa en M4.)

## 9. Sesión y autorización

- `POST /auth/register`: valida email único; hashea password con **BCrypt**; persiste `users`.
- `POST /auth/session`: verifica credenciales; genera un token opaco aleatorio (32 bytes, Base64url);
  guarda **solo el hash** (SHA-256) en `sessions` con `expires_at` (config, ej. 24h); devuelve el token en claro una sola vez.
- Autenticación: filtro lee `Authorization: Bearer`, hashea, busca sesión válida (no revocada, no expirada) → `SecurityContext`.
- Autorización: `ORGANIZER` para gestión de lanzamientos; `BUYER` (o autenticado) para reservar/checkout.
  Aislamiento: un comprador solo accede a **sus** reservas/pedidos (404 si no es dueño).
- `DELETE /auth/session` marca `revoked_at`.

## 10. Mapeo de errores → HTTP (RFC 9457)

`GlobalExceptionHandler` (`@RestControllerAdvice`) produce `ProblemDetail`:

| Excepción | HTTP | `type` |
|-----------|------|--------|
| `InsufficientStockException` | 409 | `stock-insufficient` |
| `PurchaseLimitExceededException` | 409 | `purchase-limit-exceeded` |
| `LaunchNotOpenException` | 409 | `launch-not-open` |
| `OutsideSaleWindowException` | 409 | `outside-sale-window` |
| `InvalidStateTransitionException` | 409 | `invalid-state-transition` |
| Idempotencia con `request_hash` distinto | 409 | `idempotency-conflict` |
| `MethodArgumentNotValidException` / `IllegalArgumentException` | 400 | `validation-failed` |
| `Idempotency-Key` ausente | 400 | `missing-idempotency-key` |
| Recurso inexistente / no propio | 404 | `not-found` |
| Sin sesión válida | 401 | `unauthenticated` |
| Rol/permiso insuficiente | 403 | `forbidden` |
| Rate limit excedido | 429 | `rate-limited` |

## 11. Rate limiting

Filtro en memoria (sin dependencia externa) aplicado a reserva y checkout, con clave `userId` (o IP
si es anónimo) y límite configurable (ej. N peticiones/ventana). Devuelve `429` al exceder. Enfoque
suficiente para el prototipo; no pretende ser distribuido.

## 12. OpenAPI

`springdoc-openapi-starter-webmvc-ui:3.x` (`/swagger-ui`, `/v3/api-docs`). Anotaciones mínimas en
controladores; los DTOs `record` se documentan con `@Schema` donde aporte.

## 13. Configuración (fragmentos)

```yaml
eventcart:
  auth:
    session-ttl: 24h
    bcrypt-strength: 10
  idempotency:
    ttl: 24h
  checkout:
    latency: 150ms      # latencia simulada
    timeout: 5s         # por encima ⇒ PENDING
  rate-limit:
    reserve:
      capacity: 20
      window: 1m
```

## 14. Estrategia de pruebas

- **Unitarias** (ya existentes + nuevas): `PaymentSimulator`, cálculo de hashes, mapeos.
- **Integración (Testcontainers, PostgreSQL real)**:
  - **No-sobreventa**: N hilos reservando 1 unidad sobre stock finito ⇒ exactamente `stock` éxitos.
  - **Idempotencia**: mismo `Idempotency-Key` + mismo payload ⇒ misma respuesta y una sola reserva;
    payload distinto ⇒ 409.
  - **Límite por comprador** bajo concurrencia (no exceder `per_buyer_limit`).
  - **Autorización**: 401/403/aislamiento por dueño.
  - **Checkout**: APPROVE/​DECLINE/​TIMEOUT ⇒ CONFIRMED/​FAILED/​PENDING; stock coherente.
  - **Mapeo de errores** a `ProblemDetail`.

## 15. Fuera de alcance (recordatorio)

- Outbox, eventos versionados y consumidores Kafka → **M3**.
- Job de expiración, reconciliación de `PENDING`, inyección de fallos → **M4**.
- Frontend → **M6**.
