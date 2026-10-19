# EventCart — Limited Commerce Engine

Motor de comercio para **lanzamientos programados de productos físicos de disponibilidad limitada**.
Un organizador publica una venta, los compradores reservan unidades por tiempo limitado y concluyen un
**checkout simulado**. El sistema conserva consistencia de inventario y pedidos ante concurrencia,
eventos repetidos y fallos parciales.

Objetivo doble: portafolio y aprendizaje aplicado de **Apache Kafka (Nivel 2)**.

> **Progreso:** M3 (outbox + Kafka) y M4 (caducidad, reconciliación y fallos) completados. M5 (diseño UI/UX) aprobado. **M6 (frontend) en curso**: vistas de comprador, organizador y laboratorio en `frontend/`. Ver [`docs/roadmap.md`](docs/roadmap.md).

## Stack

- Java **25** (LTS) · **Spring Boot 4.1.1** · Spring Data JPA · Flyway
- **PostgreSQL** · **Apache Kafka** (desde M3)
- **React + TypeScript** (desde M6)
- JUnit · Testcontainers · k6 · Docker Compose

## Requisitos

- **JDK 25** (el sistema puede traer otro `java` por defecto; exporta `JAVA_HOME`).
- **Docker** con `docker compose`.
- Maven no hace falta: se usa el wrapper (`./mvnw`).

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
```

## Arranque local

```bash
# 1. Levantar PostgreSQL
docker compose up -d

# 2. Ejecutar la aplicación
./mvnw spring-boot:run

# 3. Comprobar salud
curl http://localhost:8080/actuator/health
```

Perfil de desarrollo con logs detallados:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Perfiles de proceso (mismo jar): `api` (web + publicador de outbox, sin consumidores), `consumer`
(consumidores, sin web) y `worker` (expiración y reconciliación). Útiles para el laboratorio de fallos.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=worker
```

Las credenciales por defecto coinciden con `docker-compose.yml`. PostgreSQL se expone en el host en
el puerto **5439** por defecto (configurable con `EVENTCART_DB_PORT`) para no chocar con un Postgres
local. Para sobrescribir credenciales o puerto, copia `.env.sample` a `.env` y ajusta (el `.env` no se versiona).

## Pruebas

```bash
./mvnw verify
```

Los tests de integración usan **Testcontainers** (requieren Docker en marcha). `./mvnw verify`
compila y ejecuta toda la suite; es el mismo comando que corre en CI.

### Frontend

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173 (proxy /api y /actuator a :8080)
npm run test     # tests de componentes (vitest)
npm run test:e2e # recorrido completo en el navegador (Playwright; ver docs/e2e.md)
npm run lint     # oxlint
npm run build    # tsc -b + vite build
```

El frontend consume la API en `http://localhost:8080` mediante el proxy de Vite; para apuntar a otro
host, define `VITE_API_BASE`. El token de sesión (Bearer) se guarda en `localStorage`.

## Estructura

```
src/main/java/com/eventcart/
  launch/       lanzamientos: creación, publicación, programación
  reservation/  captura atómica de stock, expiración, estados
  checkout/     pago simulado y resultados
  order/        confirmación de pedidos y reconciliación de pendientes
  eventing/     outbox, eventos y consumidores (M3)
  identity/     identidad mínima de comprador/organizador
  shared/       utilidades compartidas
src/main/resources/
  application.yml · application-local.yml · application-worker.yml
  application-api.yml · application-consumer.yml
  db/migration/  migraciones Flyway
frontend/         React + TypeScript (M6): vistas comprador/organizador/laboratorio
docs/roadmap.md  checklist de hitos
scripts/lab/     inyección de fallos (solo desarrollo)
```

## Comandos útiles

```bash
docker compose up -d        # levantar Postgres
docker compose logs -f      # logs de Postgres
docker compose down         # detener
docker compose down -v      # detener y borrar datos
```

## Documentación

- [`docs/roadmap.md`](docs/roadmap.md) — hitos, decisiones y criterios de aceptación.
- [`docs/e2e.md`](docs/e2e.md) — pruebas end-to-end (Playwright) del recorrido comercial (M6).
- [`docs/design/`](docs/design/README.md) — diseño UI/UX (M5): brief, tokens, wireframes, prototipo, a11y.
- [`docs/failure-lab.md`](docs/failure-lab.md) — laboratorio de fallos y scripts dev-only (`scripts/lab/`).
- [`AGENTS.md`](AGENTS.md) — alcance, invariantes y decisiones cerradas.
