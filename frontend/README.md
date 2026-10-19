# EventCart — frontend

Interfaz de EventCart (M6): vista de **comprador**, panel de **organizador** y **laboratorio** operativo
mínimo. Construida con **Vite + React + TypeScript + Tailwind 4 + TanStack Query + react-router**,
sobre el sistema de diseño del hito M5.

El contrato de diseño vive en el repositorio (`../docs/design/`), no en Figma. `docs/design/tokens.css`
es la **fuente única de verdad** de los tokens y se importa desde `src/index.css`.

## Comandos

```bash
npm install
npm run dev      # http://localhost:5173 (proxy /api y /actuator a http://localhost:8080)
npm run test     # tests de componentes (vitest + testing-library)
npm run test:e2e # pruebas end-to-end (Playwright; ver ../docs/e2e.md)
npm run lint     # oxlint
npm run build    # tsc -b + vite build
```

## Decisiones relevantes (M6)

- **Actualizaciones en vivo:** polling con `refetchInterval` de TanStack Query; sin SSE/WebSockets en V1.
- **Sesión:** token Bearer en `localStorage` (clave `eventcart.token`), sin refresh tokens.
- **Rutas:** `react-router` con layout compartido y guardas por rol (`RequireAuth` / `RequireRole`).
- **Idioma:** solo español, con `Intl` para moneda y fechas.
- **Sin truth del cliente:** el stock/disponibilidad salen del servidor; el contador de caducidad es
  **informativo**. Las reservas y el checkout envían `Idempotency-Key`.

Para apuntar la API a otro host, define `VITE_API_BASE` (por defecto se usa el proxy de Vite).

## Pruebas end-to-end

`npm run test:e2e` ejecuta el recorrido comercial completo en el navegador. Requiere
`docker compose up -d`; el `webServer` de Playwright levanta el backend (`../mvnw spring-boot:run`, JDK 25)
y el dev server de Vite automáticamente. Detalle de escenarios y limitaciones en [`../docs/e2e.md`](../docs/e2e.md).
