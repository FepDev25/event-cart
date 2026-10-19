# Pruebas end-to-end (Playwright) — M6

Recorrido comercial completo en el navegador contra el stack real (PostgreSQL + Kafka + Spring Boot +
React). Verifican el **criterio de aceptación de M6**: crear/publicar → reservar → checkout → resultado
desde la UI, con las guardas de rol y el laboratorio operativo.

## Requisitos

- `docker compose up -d` (PostgreSQL y Kafka) en la raíz del repo.
- JDK 25 disponible (`JAVA_HOME`; por defecto el config usa `/usr/lib/jvm/java-25-openjdk`).
- Dependencias del frontend instaladas y el navegador de Playwright descargado:

```bash
cd frontend
npm install
npx playwright install chromium
```

`playwright.config.ts` arranca **automáticamente** el backend (`../mvnw spring-boot:run`) y el frontend
(`npm run dev`) mediante `webServer`, y reutiliza los procesos ya en marcha.

## Ejecución

```bash
cd frontend
npm run test:e2e              # toda la suite (un worker, en serie)
npx playwright test e2e/buyer-journey.spec.ts   # un archivo
PLAYWRIGHT_HTML_OPEN=never npm run test:e2e     # sin abrir el reporte HTML
```

Artefactos: `frontend/test-results/` (capturas en fallo) y `frontend/playwright-report/` (reporte HTML),
ambos ignorados por git.

## Escenarios

Cada test crea sus propios usuarios con correos únicos (`*@eventcart.test`) y su propio lanzamiento, por
lo que son independientes y no dependen de datos semilla del entorno.

### 1. Recorrido comercial completo — `e2e/buyer-journey.spec.ts`

1. El organizador se registra.
   - expect: aparece el botón «Salir» (sesión iniciada).
2. Crea un lanzamiento (stock 3, apertura en el pasado, cierre en +60 min) y lo abre desde el panel.
   - expect: la fila muestra el estado «Abierto».
3. Cierra sesión y el comprador se registra; abre el detalle desde la home.
   - expect: URL `/launch/<uuid>`, panel «Disponibilidad» visible.
4. Reserva una unidad.
   - expect: «Tu reserva» visible y contador informativo «para completar la compra».
5. Completa el checkout con el token `APPROVE`.
   - expect: diálogo «Pago simulado», luego «Pago aprobado. Tu pedido quedó confirmado.» y estado
     «Confirmado».

### 2. Pago rechazado — `e2e/checkout-declined.spec.ts`

1. Organizador publica un lanzamiento con stock 1; comprador reserva la única unidad.
2. Checkout con token `DECLINE` → expect: mensaje «No se pudo completar el pago…».
3. «Volver al lanzamiento» → expect: el botón «Reservar 1 unidad» reaparece (la unidad se liberó, sin
   sobreventa ni stock perdido).

### 3. Acceso y guardas de rol — `e2e/access-and-roles.spec.ts`

1. Invitado visita `/org` y `/lab` → expect: redirección a `/login`.
2. Comprador visita `/org` → expect: redirección a `/` (home de lanzamientos).
3. Organizador abre «Laboratorio» → expect: métricas de outbox y DLQ visibles.

## Limitaciones

- Los datos persisten en el PostgreSQL local; cada corrida agrega usuarios y lanzamientos nuevos (no hay
  limpieza automática). Los correos son únicos por corrida para evitar colisiones.
- No se prueba el camino `TIMEOUT`/`PENDING` (reconciliación asíncrona) en E2E: queda cubierto por las
  pruebas de integración del backend (`CheckoutExpiryRaceIT`, `ReconciliationIT`).
