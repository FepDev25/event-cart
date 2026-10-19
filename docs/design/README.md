# Diseño — EventCart (M5)

Dirección: **marketplace limpio y confiable**, solo claro. Fuente de verdad en el repo (sin Figma).

| Artefacto | Archivo | Qué contiene |
|-----------|---------|--------------|
| Brief | [`design-brief.md`](design-brief.md) | dirección, principios, voz, anti-patrones, decisiones |
| Tokens (doc) | [`design-tokens.md`](design-tokens.md) | capas, mapping de estados, contraste, integración shadcn |
| Tokens (código) | [`tokens.css`](tokens.css) | variables CSS (primitivo → semántico → componente) |
| Wireframes | [`wireframes.md`](wireframes.md) | 3 vistas en baja fidelidad + estados |
| Componentes | [`components.md`](components.md) | catálogo priorizado (entrada M6) |
| Prototipo | [`prototype/reservation.html`](prototype/reservation.html) | 1 pantalla navegable (reserva del comprador) |
| Accesibilidad | [`a11y-checklist.md`](a11y-checklist.md) | checklist WCAG 2.2 AA por vista |

## Ver el prototipo

Abre `docs/design/prototype/reservation.html` en el navegador (usa `../tokens.css` y Google Fonts
por red). Es solo UI: los botones simulan estados (abierto / reserva / checkout / expirado / agotado).

## Para M6

1. Mapear `tokens.css` a las variables de shadcn/Tailwind (ver `design-tokens.md §6`).
2. Implementar componentes P0 de `components.md` y luego las vistas de `wireframes.md`.
3. Cumplir `a11y-checklist.md` por vista.
