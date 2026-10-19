# Roadmap — EventCart

> Checklist vivo de hitos. Complementa a `AGENTS.md` (decisiones cerradas y alcance); este documento **no** amplía alcance, lo operacionaliza.
> Modelo incremental: **scaffolding → dominio + diseño de DB → backend + lógica → Outbox/Kafka → caducidad/fallos → diseño UI/UX → frontend → laboratorio**.
> El frontend **no** se construye directo: primero hay un hito de planificación de diseño/UI-UX.

---

## Cómo usar este roadmap

- Leyenda de estado: `[ ]` pendiente · `[~]` en curso · `[x]` hecho · `[!]` bloqueado.
- Cada hito tiene la misma forma:
  1. **Objetivo** — resultado observable.
  2. **Fase de decisiones previa** (solo si aplica) — ronda de preguntas al propietario con opciones; se resuelve **antes** de codear. Si un hito no la necesita, se indica explícitamente.
  3. **Tareas** — checklist de sub-tareas verificables.
  4. **Criterio de aceptación (DoD)** — cómo se demuestra que el hito está cerrado.
  5. **Fuera de alcance / notas** — para no desbordar.
- Regla de cierre de hito: informar **qué funciona, cómo reproducirlo, qué garantías se probaron, métricas reales observadas y siguiente paso mínimo**.
- Regla de arranque: **nunca** empezar un hito sin resolver su fase de decisiones (si la tiene).

## Principio rector

Demostrar con pruebas y fallos reproducibles que EventCart vende unidades limitadas **sin sobreventa** y procesa eventos de negocio correctamente en **Kafka**. UX usable + garantías medibles > arquitectura distribuida aparatosa.

## Stack confirmado

- Java **25** (LTS) + **Spring Boot** estable + Spring Kafka.
- **Maven** (con Maven Wrapper en el repo).
- **PostgreSQL** · **Apache Kafka** · **React + TypeScript**.
- **JUnit + Testcontainers** (integración) · **k6** (carga) · **Docker Compose** local.
- Monolito modular (API + persistencia + componentes de eventos + consumidores aislables).

## Identidad y decisiones base ya tomadas

- Comprador: **usuario simple + token de sesión** emitido por el backend (sin OAuth/roles empresariales).
- Infra local: **PostgreSQL primero**; **Kafka entra en el hito M3**.
- Fase de diseño frontend: **documento de diseño dedicado + tokens + librería de componentes + prototipo de 1 pantalla** antes de construir.

## Progreso general

| Hito | Nombre | Estado | Fase de decisiones |
|------|--------|--------|--------------------|
| M0 | Scaffolding y fundaciones | `[x]` | Resuelta |
| M1 | Dominio + diseño de base de datos | `[x]` | Resuelta |
| M2 | Backend: reserva atómica + checkout simulado | `[x]` | Resuelta |
| M3 | Outbox transaccional + Kafka | `[x]` | Resuelta |
| M4 | Caducidad, reconciliación y fallos | `[ ]` | Resuelta |
| M5 | Diseño UI/UX (previo al frontend) | `[ ]` | Sí |
| M6 | Frontend comprador / organizador / laboratorio mínimo | `[ ]` | Sí |
| M7 | Laboratorio de portafolio (k6, métricas, docs) | `[ ]` | Sí |

## Invariantes transversales (se validan en cada hito aplicable)

- [ ] `disponibles + reservadas activas + confirmadas = stock total` por lanzamiento; sin negativos.
- [ ] Cero sobreventa bajo concurrencia.
- [ ] Reserva + descuento de disponibilidad en **una** transacción PostgreSQL.
- [ ] La apertura/cierre de venta se autoriza en el **servidor** (hora y zona explícitas).
- [ ] Estados de reserva: `RESERVED -> CONFIRMED | RELEASED | EXPIRED`; sin transiciones desde estados terminales.
- [ ] Expiración vía `expires_at` persistente; **no** depende de un consumidor Kafka activo.
- [ ] Liberación de stock **exactamente una vez** incluso bajo carrera checkout/expiración.
- [ ] Idempotencia en reserva/checkout (mismo resultado ante reintento; rechazo si misma clave con contenido distinto).
- [ ] Postgres es fuente de verdad de stock/reservas/pedidos; Kafka transporta hechos asíncronos (at-least-once).
- [ ] Contratos de eventos versionados; sin cambios incompatibles silenciosos.
- [ ] Sin datos personales en eventos, logs o métricas.

---

## M0 — Scaffolding y fundaciones

**Objetivo:** repositorio compilable, arrancable y reproducible, con estructura de monolito modular y PostgreSQL local, sin lógica de dominio todavía.

### Fase de decisiones previa — RESUELTA
- [x] Java: **25 LTS** (`/usr/lib/jvm/java-25-openjdk`, 25.0.4.1).
- [x] Spring Boot: **4.1.1** (última estable, jun 2026).
- [x] Estructura: **módulo Maven único + paquetes por bounded context** (`launch`, `reservation`, `checkout`, `order`, `eventing`, `identity`, `shared`).
- [x] Migraciones: **Flyway** (migraciones SQL explícitas).
- [x] Persistencia: **Spring Data JPA + SQL nativo en rutas críticas** (reserva/stock).
- [x] Tooling: **records + Lombok + MapStruct**.
- [x] Paquete/groupId base: **`com.eventcart`**.
- [x] CI: **GitHub Actions mínimo en M0** (build + test).
- [x] Puerto host de PostgreSQL: **5439** por defecto (5432 está ocupado por un Postgres del sistema); configurable con `EVENTCART_DB_PORT`.

### Tareas
- [x] `git init` ya existe → primer `.gitignore` (Java, Node, IDE, `.env`).
- [x] `pom.xml` + Maven Wrapper (`mvnw`, `mvnw.cmd`).
- [x] Estructura de carpetas/paquetes por bounded context: `launch`, `reservation`, `checkout`, `order`, `eventing` (placeholder), `identity`, `shared`.
- [x] `application.yml` + `application-local.yml` + `.env.sample` (sin secretos).
- [x] `docker-compose.yml` con **PostgreSQL** (sin Kafka aún).
- [x] Endpoint de salud y arranque en local (`/actuator/health`).
- [x] README raíz con requisitos y comandos de arranque.
- [x] `docs/` con este roadmap.

### Criterio de aceptación
`./mvnw spring-boot:run` arranca contra Postgres de Compose; `/actuator/health` responde `UP`; build limpio (`./mvnw verify`) sin tests aún o con un test de humo.

### Fuera de alcance / notas
- Sin entidades de dominio ni endpoints funcionales.
- Sin Kafka (llega en M3).

---

## M1 — Dominio + diseño de base de datos

**Objetivo:** modelo de dominio de inventario/reserva/pedido y esquema PostgreSQL con constraints que hagan imposible la inconsistencia, más la lógica de negocio pura testeable (sin HTTP).

### Fase de decisiones previa — RESUELTA
- [x] **Modelo contable**: contadores en `launches` (`available_units`/`reserved_units`/`confirmed_units`) con `CHECK` y `available+reserved+confirmed = total_stock`.
- [x] **Granularidad**: una reserva por fila con `quantity`.
- [x] **No-sobreventa/lock**: `UPDATE` condicional atómico (`WHERE ... available_units >= :qty`), sin locks explícitos.
- [x] **Estados**: `Reservation` `RESERVED→CONFIRMED|RELEASED|EXPIRED`; ciclo de checkout en `Order` `PENDING→CONFIRMED|FAILED`.
- [x] **Tipos**: `numeric(12,2)`/`numeric(14,2)` + `currency char(3)`; `expires_at timestamptz` (UTC).
- [x] **Límite por comprador**: habilitado, tabla `buyer_launch_usage`, configurable por lanzamiento, default sin límite.
- [x] **TTL de reserva**: `reservation_ttl_seconds` por lanzamiento, default 600 (10 min).
- [x] **PKs**: `uuid` v4 generados en la app.
- [x] **Producto**: embebido en `launches` (sin tabla `product`).
- [x] **Ciclo de `Launch`**: `DRAFT/SCHEDULED/OPEN/CLOSED/CANCELLED`; se congelan precio/stock/schedule al `OPEN`.
- [x] **Idempotencia**: tabla `idempotency_keys` con `UNIQUE(scope, key)`.
- [x] **Convenciones**: enums como `text` + `CHECK`, tablas snake_case en plural, una migración `V1__core_schema.sql`.
- Referencia: [`docs/domain-model.md`](../docs/domain-model.md) y [`V1__core_schema.sql`](../src/main/resources/db/migration/V1__core_schema.sql).

### Tareas
- [x] Entidades/agregados de dominio: `Launch`, `Reservation`, `Order` con máquina de estados encapsulada.
- [x] Migraciones de esquema (herramienta elegida en M0).
- [x] Constraints de integridad en DB (no negatividad, unicidad, `expires_at`).
- [x] Servicio de dominio para reservar (`ReservationService`) sin dependencia de HTTP; la atomicidad real se cierra en M2.
- [x] Lógica de transiciones válidas y rechazo de transiciones inválidas.
- [x] Tests unitarios de reglas y máquina de estados (sin DB): 42 tests.
- [x] Documento corto `docs/domain-model.md` con estados, transiciones e invariante contable.

### Criterio de aceptación
Tests unitarios verdes que cubren: transiciones válidas/ inválidas, cálculo de disponibilidad, y que una reserva no puede dejar `available` negativo a nivel de modelo. Esquema aplicado en Postgres vía migración reproducible.

### Fuera de alcance / notas
- Sin endpoints HTTP ni transacciones concurrentes reales todavía (van a M2).
- Sin Kafka.

---

## M2 — Backend: reserva atómica + checkout simulado (vertical sin Kafka)

**Objetivo:** flujo completo por HTTP: crear/publicar lanzamiento → reservar atómicamente → checkout simulado → confirmar o liberar. Validado con pruebas de concurrencia y **no sobreventa**.

### Fase de decisiones previa — RESUELTA
- [x] **Persistencia**: dominio puro + entidades JPA separadas + MapStruct + factories de rehidratación (id explícito).
- [x] **Contrato REST**: `/api/v1` RESTful con acciones de estado (`POST /launches`, `/launches/{id}/publish|cancel`, `POST /launches/{id}/reservations`, `POST /reservations/{id}/checkout|cancel`, `GET /orders/{id}`).
- [x] **Identidad**: `POST /auth/register` + `POST /auth/session`; usuario email + password (BCrypt) con rol `BUYER`/`ORGANIZER`.
- [x] **Sesión**: token opaco persistido en `sessions` (hash + `expires_at` + revocación), `Authorization: Bearer`.
- [x] **Autorización**: rol `ORGANIZER` para gestión; `BUYER` para reservar/checkout; aislamiento por comprador.
- [x] **Idempotencia**: header `Idempotency-Key` obligatorio en reserva y checkout; TTL 24h; mismo payload → misma respuesta; payload distinto → 409.
- [x] **Checkout simulado**: payment token ficticio en el request (`APPROVE`/`DECLINE`/`TIMEOUT`) + latencia configurable.
- [x] **Checkout pendiente**: síncrono con timeout corto; resultado indeterminado → `Order` `PENDING` (202) + conciliación; ventana de gracia protege la reserva.
- [x] **Errores**: RFC 9457 `application/problem+json` (Spring `ProblemDetail`), excepciones de dominio mapeadas a HTTP.
- [x] **OpenAPI**: springdoc-openapi 3.x (`/swagger-ui`).
- [x] **Rate limit**: básico en memoria (IP/usuario) sobre reserva y checkout, configurable.
- Referencia de esquema: [`V1__core_schema.sql`](../src/main/resources/db/migration/V1__core_schema.sql) (tablas `orders`, `idempotency_keys`, `buyer_launch_usage` ya definidas).
- Referencia de diseño: [`docs/m2-api-design.md`](../docs/m2-api-design.md) (contrato REST, persistencia y flujos).

### Tareas
- [x] Endpoints de organizador: crear, editar, publicar/programar, cancelar lanzamiento.
- [x] Endpoints de comprador: ver lanzamiento publicado, solicitar reserva, consultar estado.
- [x] Transacción de reserva con bloqueo/actualización condicional; reintento seguro.
- [x] Idempotencia de reserva y de checkout.
- [x] Límite por comprador (si se habilitó en M1) aplicado atómicamente.
- [x] Autorización servidor de apertura/cierre (hora + zona).
- [x] Checkout simulado con resultados aprobado/rechazado/fallo-timeout.
- [x] Cambio de estado `RESERVED -> CONFIRMED | RELEASED` en la misma frontera transaccional del stock.
- [x] Validación de cantidades, estados y fechas en servidor; rate limit básico.
- [x] Identidad mínima: registro/login simple + emisión de token.
- [x] Tests de integración con Testcontainers: reserva concurrente, idempotencia, límites, autorización.
- [x] **Prueba de no sobreventa**: N reservas concurrentes sobre stock finito → exactamente 5/20 en el escenario de prueba.

### Criterio de aceptación
Tests de integración demuestran: cero sobreventa, sin reservas duplicadas por reintento, invariante contable intacto, rechazos claros por stock/estado/fecha, y que la ruta de error **no** confirma pedidos ni libera/pierde stock indebidamente.

### Fuera de alcance / notas
- Sin Kafka (M3). Sin frontend (M6).
- Expiración automática completa se afina en M4 (aquí basta el modelo `expires_at`).

---

## M3 — Outbox transaccional + Kafka

**Objetivo:** emitir eventos de negocio **realmente comprometidos** vía Transactional Outbox y procesarlos con consumidores idempotentes con efectos observables.

### Fase de decisiones previa — RESUELTA
- [x] **Contrato**: envelope JSON autocontenido `{eventId, type, version, aggregateId, occurredAt, payload}`.
- [x] **Serialización**: JSON (Jackson 3) versionado.
- [x] **Topics/particionado**: un topic por dominio (`eventcart.reservation`, `eventcart.order`); clave = `aggregateId`.
- [x] **Outbox**: `outbox(id, aggregate_id, event_type, payload jsonb, occurred_at, published_at nullable, attempts, created_at)`.
- [x] **Publicador**: `@Scheduled` polling por batch, publica y marca `published_at` (recuperación tras reinicio), orden por agregado.
- [x] **Consumidores**: 3 grupos — Notifications (efecto ficticio), Sales Projection (tabla de lectura), Audit/Operations (append-only).
- [x] **Deduplicación**: tabla `processed_events(consumer, event_id)` UNIQUE, insertada en la misma tx que el efecto.
- [x] **Reintentos/DLQ**: Spring Kafka `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` a `<topic>.DLT` con backoff.
- [x] **Garantía**: ack tras aplicar el efecto; semántica **at-least-once** (sin prometer exactly-once).
- [x] **Eventos M3**: `ReservationCreated`, `ReservationReleased`, `OrderConfirmed`, `PaymentSimulationFailed`.
- [x] **Compose**: Apache Kafka en modo **KRaft** + Kafka UI.
- [x] **Tests**: Testcontainers Kafka (outbox→publicación, redelivery, dedup, DLQ).
- Stack verificado: `spring-boot-starter-kafka` (spring-kafka 4.1.x, kafka-clients 4.3.x).
- Referencia de diseño: [`docs/m3-kafka-design.md`](../docs/m3-kafka-design.md) (contrato, outbox, consumidores y DLT).

### Tareas
- [x] Tabla `outbox` + escritura en la **misma transacción** del cambio de estado.
- [x] Publicador separado con registro de intentos y recuperación tras reinicio.
- [x] Eventos: `ReservationCreated`, `ReservationReleased`, `OrderConfirmed`, `PaymentSimulationFailed`.
- [x] Consumidor **Notificaciones** (efecto ficticio verificable).
- [x] Consumidor **Proyección de ventas** (tabla de lectura con retraso visible).
- [x] Consumidor **Auditoría/Operaciones**.
- [x] Deduplicación persistente por `event_id`/consumidor; efectos idempotentes.
- [x] Manejo explícito de eventos desconocidos y versiones incompatibles.
- [x] Reintentos con backoff → DLQ para irrecuperables.
- [x] Compose con Kafka (KRaft) + Kafka UI.
- [x] Tests con Testcontainers (Kafka): outbox→publicación, redelivery, dedup, DLQ.

### Criterio de aceptación
Un cambio de estado confirmado aparece en Kafka en un instante acotado; reentregar el mismo evento **no** duplica efectos; un evento inválido termina en DLQ; reiniciar el publicador tras un fallo publica lo pendiente **sin** duplicar efectos visibles. Se documenta explícitamente que la semántica es **at-least-once**.

### Fuera de alcance / notas
- No se declara exactly-once end-to-end.
- Sin Kafka Streams / event sourcing.

---

## M4 — Caducidad, reconciliación y fallos

**Objetivo:** expiración correcta e idempotente de reservas, reconciliación de estados y pruebas de fallos reproducibles (reinicios, duplicados, carreras).

### Fase de decisiones previa — RESUELTA
- [x] **Mecanismo de expiración**: **proceso/worker separado** (mismo jar, perfil Spring dedicado con `web-application-type=none`); job `@Scheduled` con claim atómico (`UPDATE ... WHERE status='RESERVED' AND expires_at <= now()`) ⇒ liberación exacta una sola vez; intervalo configurable.
- [x] **Carrera checkout/expiración**: **ventana de gracia**. El expirador respeta reservas con `Order PENDING` dentro de una ventana (p.ej. 2× timeout de checkout). Aprobado dentro de la ventana confirma; si la reserva ya expiró, el `Order` queda `FAILED` **sin** re-liberar stock (la liberación ya ocurrió exactamente una vez).
- [x] **Reconciliación**: **job periódico único** que resuelve `Order` `PENDING` vencidos, detecta reservas `RESERVED` caducadas sin liberar y expone contadores de inconsistencia (logs/métricas).
- [x] **Herramientas de inyección de fallos** (solo dev/laboratorio): **reusar lo existente + scripts**. El checkout ya soporta `APPROVE`/`DECLINE`/`TIMEOUT` y latencia configurable; para consumidor/publicador, scripts de Compose (`stop`/`start`). Sin endpoints nuevos.
- [x] **Backoff/reintentos DLT**: **backoff configurable** por properties (base 500ms×3 hoy) y **reproceso desde DLT manual/controlado** (script), apoyado en la deduplicación por `event_id`.

### Tareas
- [x] Job de expiración basado en `expires_at` en **proceso worker** (perfil `worker`, sin web); no depende de Kafka.
- [x] Emisión de `ReservationExpired` vía outbox.
- [x] Liberación de stock **una sola vez** bajo carrera con confirmación (ventana de gracia). El barrido excluye reservas con `Order PENDING` creado dentro de `grace-window`; un APPROVE que llega tras la expiración deja `Order FAILED` sin re-liberar stock.
- [x] Reconciliación periódica (`ReconciliationService`/`ReconciliationScheduler` en worker): resuelve `Order PENDING` vencidos → `FAILED` + libera reserva **condicionalmente** + emite `PaymentSimulationFailed`; expone `eventcart.reconciliation.orders_failed` y gauge `eventcart.reconciliation.stale_reserved`.
- [ ] Vías de inyección de fallos dev-only (sin exponer en público).
- [x] Tests: expiración simultánea con checkout (`CheckoutExpiryRaceIT`), ventana de gracia (`ReservationExpiryGraceIT`), reconciliación idempotente (`ReconciliationIT`), reinicio durante publicación outbox (`OutboxPublisherIT`).
- [ ] Documento `docs/failure-lab.md` con qué invariantes permanecen verdaderos.

### Criterio de aceptación
Escenarios reproducibles demuestran: reservas caducadas liberadas exactamente una vez; sin sobreventa ni stock perdido en carreras; reinicio de consumidor recupera pendientes sin duplicar efectos.

### Fuera de alcance / notas
- Inyección de fallos solo local/laboratorio.

---

## M5 — Diseño UI/UX (previo al frontend)

**Objetivo:** dirección visual y sistema de diseño listos **antes** de escribir componentes de producto, para un resultado profesional.

### Fase de decisiones previa (ronda de preguntas)
- [ ] **Dirección visual / tono** (marca, paleta, tipografía) acorde al dominio de "lanzamiento limitado".
- [ ] **Stack frontend**: Vite + React + TS; **shadcn/ui + Tailwind** u otra librería; gestor de estado/datos (p. ej. TanStack Query).
- [ ] **Design tokens**: color, tipografía, espaciado, radios, sombras, estados (éxito/error/pendiente/degradado).
- [ ] **Wireframes** de las 3 vistas: comprador, organizador, laboratorio mínimo.
- [ ] **Prototipo** de una pantalla representativa (p. ej. reserva con contador de caducidad informativo).
- [ ] **Accesibilidad** (contraste, foco, navegación teclado) y responsive.
- [ ] ¿Existe un archivo **Figma** de referencia? (si sí, indica enlace/keys).

### Tareas
- [ ] `docs/design/design-brief.md`: dirección, referencias y principios.
- [ ] `docs/design/design-tokens.md` (+ variables CSS).
- [ ] `docs/design/wireframes.md` (o imágenes en `docs/design/`).
- [ ] Catálogo inicial de componentes (botón, input, card de lanzamiento, contador, estados, tabla organizador, panel laboratorio).
- [ ] Prototipo navegable de 1 pantalla clave.
- [ ] Checklist de accesibilidad por vista.

### Criterio de aceptación
Existe un brief + tokens + wireframes de las 3 vistas + prototipo de 1 pantalla, aprobados por el propietario, y una lista de componentes a implementar en M6.

### Fuera de alcance / notas
- Sin lógica de negocio ni integración real con la API todavía.
- El contador de caducidad es **informativo**; el stock confirmado nunca se infiere del cliente.

---

## M6 — Frontend comprador / organizador / laboratorio mínimo

**Objetivo:** experiencia de compra coherente y herramientas de organizador, sobre el sistema de diseño de M5 y la API de M2–M4.

### Fase de decisiones previa (ronda de preguntas)
- [ ] **Actualizaciones en vivo**: polling vs SSE (WebSockets solo si se justifica) por vista.
- [ ] **Gestión de sesión** en frontend (almacenamiento del token, refresh).
- [ ] **Rutas** y navegación.
- [ ] **i18n** (¿es/en?) y formato de moneda/fechas.
- [ ] **UX de degradación**: reserva caducada, checkout pendiente, sistema degradado, stock agotado.

### Tareas
- [ ] Scaffold del frontend con el stack decidido en M5.
- [ ] Implementar tokens y componentes base.
- [ ] Vista comprador: lanzamiento, precio, stock/estado informativo, tiempo hasta apertura, reservar, contador informativo, checkout simulado con estados claros.
- [ ] Vista organizador: CRUD mínimo, publicación/programación, inventario, métricas de reservas/ventas, reglas de cambio tras abrir venta.
- [ ] Vista laboratorio mínimo: estado de procesamiento, incidencias, duplicados detectados, exploración de DLQ, métricas de consistencia.
- [ ] Actualizaciones en vivo (polling/SSE) donde aporte.
- [ ] UX explícita para caducidad/pendiente/degradado/agotado.
- [ ] Tests de componentes/UI para flujos críticos.

### Criterio de aceptación
El recorrido comercial completo se hace desde el navegador (crear/publicar → reservar → checkout → resultado) y el organizador ve inventario y métricas; el laboratorio muestra estado de eventos/duplicados/DLQ sin sustituir herramientas de observabilidad reales.

### Fuera de alcance / notas
- Sin simulador de carga en el navegador (los scripts son la fuente de verdad).
- WebSockets solo si el caso lo justifica.

---

## M7 — Laboratorio de portafolio (k6, métricas, documentación)

**Objetivo:** evidencia reproducible de los invariantes y de la confiabilidad de Kafka, con métricas reales medidas y documentadas.

### Fase de decisiones previa (ronda de preguntas)
- [ ] **Escenario k6**: proporción reserva/checkout, rampa de VUs, duración y datos semilla.
- [ ] **Entorno/hardware** a documentar y límites conocidos.
- [ ] **Experimentos de fallo** a incluir (caída de consumidor, duplicados, reinicio de publicador, DLQ).
- [ ] **Herramientas de observabilidad mínima** ya existentes que basten (métricas/paneles).

### Tareas
- [ ] Script k6: **100 unidades / ~1.000 clientes virtuales** como escenario ilustrativo.
- [ ] Verificación automatizada de invariantes: (a) cero sobreventa, (b) sin reservas duplicadas, (c) suma de stock consistente, (d) caducadas liberadas una vez, (e) pedidos coherentes con pagos simulados, (f) reinicio recupera pendientes sin duplicar efectos.
- [ ] Medición de throughput, p50/p95/p99, tasa de errores, aprobadas/rechazadas, tiempo de publicación outbox, lag de consumidores, duplicados, tiempo de recuperación.
- [ ] `docs/benchmarks.md` con resultados, hardware, entorno, versión y limitaciones.
- [ ] README que permita **reproducir toda la prueba localmente**.

### Criterio de aceptación (Definition of Done del primer producto)
Crear lanzamiento, reservar atómicamente, completar/rechazar checkout simulado, expirar reservas, confirmar pedidos y observar eventos consumidos; escenario reproducible demuestra los invariantes; el reinicio de consumidor recupera pendientes; el README reproduce la prueba localmente.

### Fuera de alcance / notas
- No presentar como benchmark logrado ni afirmar escala en producción.
- Nunca inventar métricas.

---

## Apéndice A — Decisiones abiertas heredadas de AGENTS.md

Estas se resolverán en la fase de decisiones del hito correspondiente:

| Decisión abierta | Hito |
|------------------|------|
| Valor de tiempo de reserva y límites de compra | M1 |
| Política de checkout pendiente que excede vencimiento | M2 / M4 |
| Persistencia y protocolo del pago simulado | M2 |
| Esquema outbox/DLQ, particionado, tooling de observabilidad | M3 / M4 |
| Identidad/autenticación mínima y UX/UI final | M2 / M5 |
| Momento del módulo de entradas generales (sin compromiso en V1) | Post-V1 |

## Apéndice B — Dependencias entre hitos

```
M0 ──▶ M1 ──▶ M2 ──▶ M3 ──▶ M4 ──▶ M6 ──▶ M7
                       │            ▲
                       └──▶ M5 ─────┘
```

- M3 depende de M2 (estados comprometidos que emitir).
- M5 (diseño) puede iniciarse en paralelo tras M2, pero **precede** a M6.
- M7 requiere el sistema integrado (M3–M4) y el frontend mínimo (M6) para la demo.
- **No** iniciar Kafka (M3) antes de garantizar la transacción básica (M2).
