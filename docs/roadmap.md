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
| M0 | Scaffolding y fundaciones | `[~]` | Resuelta |
| M1 | Dominio + diseño de base de datos | `[ ]` | Sí |
| M2 | Backend: reserva atómica + checkout simulado | `[ ]` | Sí |
| M3 | Outbox transaccional + Kafka | `[ ]` | Sí |
| M4 | Caducidad, reconciliación y fallos | `[ ]` | Sí |
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

### Tareas
- [ ] `git init` ya existe → primer `.gitignore` (Java, Node, IDE, `.env`).
- [ ] `pom.xml` + Maven Wrapper (`mvnw`, `mvnw.cmd`).
- [ ] Estructura de carpetas/paquetes por bounded context: `launch`, `reservation`, `checkout`, `order`, `eventing` (placeholder), `identity`, `shared`.
- [ ] `application.yml` + `application-local.yml` + `.env.sample` (sin secretos).
- [ ] `docker-compose.yml` con **PostgreSQL** (sin Kafka aún).
- [ ] Endpoint de salud y arranque en local (`/actuator/health`).
- [ ] README raíz con requisitos y comandos de arranque.
- [ ] `docs/` con este roadmap.

### Criterio de aceptación
`./mvnw spring-boot:run` arranca contra Postgres de Compose; `/actuator/health` responde `UP`; build limpio (`./mvnw verify`) sin tests aún o con un test de humo.

### Fuera de alcance / notas
- Sin entidades de dominio ni endpoints funcionales.
- Sin Kafka (llega en M3).

---

## M1 — Dominio + diseño de base de datos

**Objetivo:** modelo de dominio de inventario/reserva/pedido y esquema PostgreSQL con constraints que hagan imposible la inconsistencia, más la lógica de negocio pura testeable (sin HTTP).

### Fase de decisiones previa (ronda de preguntas)
- [ ] **Modelo contable de inventario**: columnas contables en `launch` (`available`/`reserved`/`confirmed`) vs derivar de filas de reserva/ledger. (Decide el mecanismo de no-sobreventa.)
- [ ] **Granularidad de reserva**: una fila por unidad vs una fila con `quantity`. (Afecta límites y expiración.)
- [ ] **Estrategia de bloqueo**: `SELECT ... FOR UPDATE` vs `UPDATE ... WHERE available >= qty RETURNING`.
- [ ] **Estados exactos y transiciones canónicas** de `Reservation` y `Order` (incl. estado para checkout pendiente).
- [ ] **Tipos**: `price` con `BigDecimal` + moneda; precisión/escala; `expires_at` (`timestamptz` con UTC).
- [ ] **Límite por comprador**: ¿se habilita en V1? valor y unicidad (¿por lanzamiento + comprador?).
- [ ] **Tiempo de reserva** por defecto y si es configurable por lanzamiento.
- [ ] **Constraints**: `CHECK (available >= 0)`, unicidad de idempotencia, FKs y políticas de borrado.

### Tareas
- [ ] Entidades/agregados de dominio: `Launch`, `Reservation`, `Order` con máquina de estados encapsulada.
- [ ] Migraciones de esquema (herramienta elegida en M0).
- [ ] Constraints de integridad en DB (no negatividad, unicidad, `expires_at`).
- [ ] Servicio de dominio para reservar (operación atómica) sin dependencia de HTTP.
- [ ] Lógica de transiciones válidas y rechazo de transiciones inválidas.
- [ ] Tests unitarios de reglas y máquina de estados (sin DB).
- [ ] Documento corto `docs/domain-model.md` con estados, transiciones e invariante contable.

### Criterio de aceptación
Tests unitarios verdes que cubren: transiciones válidas/ inválidas, cálculo de disponibilidad, y que una reserva no puede dejar `available` negativo a nivel de modelo. Esquema aplicado en Postgres vía migración reproducible.

### Fuera de alcance / notas
- Sin endpoints HTTP ni transacciones concurrentes reales todavía (van a M2).
- Sin Kafka.

---

## M2 — Backend: reserva atómica + checkout simulado (vertical sin Kafka)

**Objetivo:** flujo completo por HTTP: crear/publicar lanzamiento → reservar atómicamente → checkout simulado → confirmar o liberar. Validado con pruebas de concurrencia y **no sobreventa**.

### Fase de decisiones previa (ronda de preguntas)
- [ ] **Contrato REST**: rutas, verbos, recursos y versionado (`/api/v1/...`).
- [ ] **Idempotency key**: header, formato, alcance y vencimiento; comportamiento ante clave repetida con payload incompatible.
- [ ] **Token de sesión**: opaco en tabla `sessions` vs firmado; expiración y revocación.
- [ ] **Checkout simulado**: cómo se decide aprobado/rechazado/timeout (seed/token determinista vs configuración por lanzamiento vs endpoint dev).
- [ ] **Política de checkout pendiente / timeout**: qué se persiste y cómo se reconcilia (adelanto de M4).
- [ ] **Contrato de errores**: formato de respuesta, códigos y mapeo HTTP.
- [ ] ¿OpenAPI/Springdoc en M2?
- [ ] **Autorización del organizador** para crear/publicar/cancelar.

### Tareas
- [ ] Endpoints de organizador: crear, editar, publicar/programar, cancelar lanzamiento.
- [ ] Endpoints de comprador: ver lanzamiento publicado, solicitar reserva, consultar estado.
- [ ] Transacción de reserva con bloqueo/actualización condicional; reintento seguro.
- [ ] Idempotencia de reserva y de checkout.
- [ ] Límite por comprador (si se habilitó en M1) aplicado atómicamente.
- [ ] Autorización servidor de apertura/cierre (hora + zona).
- [ ] Checkout simulado con resultados aprobado/rechazado/fallo-timeout.
- [ ] Cambio de estado `RESERVED -> CONFIRMED | RELEASED` en la misma frontera transaccional del stock.
- [ ] Validación de cantidades, estados y fechas en servidor; rate limit básico.
- [ ] Identidad mínima: registro/login simple + emisión de token.
- [ ] Tests de integración con Testcontainers: reserva concurrente, idempotencia, límites, autorización.
- [ ] **Prueba de no sobreventa**: N reservas concurrentes sobre stock finito → exactamente `stock` confirmadas.

### Criterio de aceptación
Tests de integración demuestran: cero sobreventa, sin reservas duplicadas por reintento, invariante contable intacto, rechazos claros por stock/estado/fecha, y que la ruta de error **no** confirma pedidos ni libera/pierde stock indebidamente.

### Fuera de alcance / notas
- Sin Kafka (M3). Sin frontend (M6).
- Expiración automática completa se afina en M4 (aquí basta el modelo `expires_at`).

---

## M3 — Outbox transaccional + Kafka

**Objetivo:** emitir eventos de negocio **realmente comprometidos** vía Transactional Outbox y procesarlos con consumidores idempotentes con efectos observables.

### Fase de decisiones previa (ronda de preguntas)
- [ ] **Contrato de eventos**: campos (`event_id`, `type`, `version`, `aggregate_id`, `occurred_at`, payload acotado) y esquema/validación.
- [ ] **Serialización**: JSON+Jackson vs Avro/schema registry (proponer JSON versionado para V1).
- [ ] **Topics y particionado**: nombres de topics y clave de partición (`aggregate_id`) para orden por agregado.
- [ ] **Publicador outbox**: polling vs CDC; batching; marca de publicado; manejo de fallos y reintentos.
- [ ] **Consumidores**: cuántos/grupos (notificación, proyección de ventas, auditoría) y qué efecto observable tiene cada uno.
- [ ] **Deduplicación**: tabla `processed_events` por consumidor y clave.
- [ ] **Retries/backoff y DLQ**: umbrales, topic de DLQ y procedimiento de reproceso controlado.
- [ ] **Compose Kafka**: modo **KRaft** vs Zookeeper; incluir Kafka UI.

### Tareas
- [ ] Tabla `outbox` + escritura en la **misma transacción** del cambio de estado.
- [ ] Publicador separado con registro de intentos y recuperación tras reinicio.
- [ ] Eventos: `ReservationCreated`, `ReservationExpired`, `OrderConfirmed`, `PaymentSimulationFailed`.
- [ ] Consumidor **Notificaciones** (efecto ficticio verificable).
- [ ] Consumidor **Proyección de ventas** (tabla de lectura con retraso visible).
- [ ] Consumidor **Auditoría/Operaciones**.
- [ ] Deduplicación persistente por `event_id`/consumidor; efectos idempotentes.
- [ ] Manejo explícito de eventos desconocidos y versiones incompatibles.
- [ ] Reintentos con backoff → DLQ para irrecuperables.
- [ ] Compose con Kafka (KRaft) + Kafka UI.
- [ ] Tests con Testcontainers (Kafka): outbox→publicación, redelivery, dedup, DLQ.

### Criterio de aceptación
Un cambio de estado confirmado aparece en Kafka en un instante acotado; reentregar el mismo evento **no** duplica efectos; un evento inválido termina en DLQ; reiniciar el publicador tras un fallo publica lo pendiente **sin** duplicar efectos visibles. Se documenta explícitamente que la semántica es **at-least-once**.

### Fuera de alcance / notas
- No se declara exactly-once end-to-end.
- Sin Kafka Streams / event sourcing.

---

## M4 — Caducidad, reconciliación y fallos

**Objetivo:** expiración correcta e idempotente de reservas, reconciliación de estados y pruebas de fallos reproducibles (reinicios, duplicados, carreras).

### Fase de decisiones previa (ronda de preguntas)
- [ ] **Mecanismo de expiración**: `@Scheduled` en la app vs proceso/worker separado; intervalo.
- [ ] **Carrera checkout/expiración**: ventana de gracia, política si el checkout gana/pierde, y estado resultante.
- [ ] **Reconciliación** de reservas huérfanas/caducadas pendientes de liberación.
- [ ] **Herramientas de inyección de fallos** (solo dev/laboratorio): endpoints o flags para checkout lento/rechazado, parar/`reproducir` mensajes.
- [ ] **Backoff/reintentos** definitivos y política de reproceso desde DLQ.

### Tareas
- [ ] Job de expiración basado en `expires_at` (no depende de Kafka).
- [ ] Liberación de stock **una sola vez** bajo carrera con confirmación.
- [ ] Emisión de `ReservationExpired` vía outbox.
- [ ] Reconciliación periódica y contadores de inconsistencia.
- [ ] Vías de inyección de fallos dev-only (sin exponer en público).
- [ ] Tests de: expiración simultánea con checkout, reinicio durante publicación outbox, duplicados, eventos fuera de orden.
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
