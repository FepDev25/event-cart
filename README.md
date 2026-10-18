# EventCart — Limited Commerce Engine

Motor de comercio para **lanzamientos programados de productos físicos de disponibilidad limitada**.
Un organizador publica una venta, los compradores reservan unidades por tiempo limitado y concluyen un
**checkout simulado**. El sistema conserva consistencia de inventario y pedidos ante concurrencia,
eventos repetidos y fallos parciales.

Objetivo doble: portafolio y aprendizaje aplicado de **Apache Kafka (Nivel 2)**.

> **Progreso:** M3 (outbox transaccional + Kafka) completado. Siguiente: M4 (caducidad, reconciliación y fallos). Ver [`docs/roadmap.md`](docs/roadmap.md) para el checklist de hitos.

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

Las credenciales por defecto coinciden con `docker-compose.yml`. PostgreSQL se expone en el host en
el puerto **5439** por defecto (configurable con `EVENTCART_DB_PORT`) para no chocar con un Postgres
local. Para sobrescribir credenciales o puerto, copia `.env.sample` a `.env` y ajusta (el `.env` no se versiona).

## Pruebas

```bash
./mvnw verify
```

Los tests de integración usan **Testcontainers** (requieren Docker en marcha). `./mvnw verify`
compila y ejecuta toda la suite; es el mismo comando que corre en CI.

## Estructura

```
src/main/java/com/eventcart/
  launch/       lanzamientos: creación, publicación, programación
  reservation/  captura atómica de stock, expiración, estados
  checkout/     pago simulado y resultados
  order/        confirmación de pedidos
  eventing/     outbox, eventos y consumidores (M3)
  identity/     identidad mínima de comprador/organizador
  shared/       utilidades compartidas
src/main/resources/
  application.yml · application-local.yml
  db/migration/  migraciones Flyway
docs/roadmap.md  checklist de hitos
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
- [`AGENTS.md`](AGENTS.md) — alcance, invariantes y decisiones cerradas.
