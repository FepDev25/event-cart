# Catálogo de componentes — entrada para M6

Lista de componentes a implementar en M6, sobre shadcn/ui + Tailwind y los tokens de
[`tokens.css`](tokens.css). Prioridad: **P0** = imprescindible para el recorrido comercial,
**P1** = organizador/laboratorio, **P2** = refinamiento.

## Base (shadcn/ui, se configuran con los tokens)

| Componente | shadcn | Uso |
|-----------|--------|-----|
| Button | `button` | variantes `primary` (ink), `accent` (escasez), `outline`, `ghost`, `destructive` |
| Input / Label / FieldError | `input` `label` `form` | formularios con `FieldError` + validación Zod |
| Card | `card` | superficies del producto y paneles |
| Badge | `badge` | estados (`available`/`pending`/`accent`/`danger`) |
| Table | `table` | listas de organizador y laboratorio |
| Dialog / AlertDialog | `dialog` | checkout simulado y confirmaciones |
| Toast / Sonner | `sonner` | feedback no bloqueante |
| Alert | `alert` | banners de degradado / error resumen |
| Tabs | `tabs` | secciones de organizador / laboratorio |
| Skeleton | `skeleton` | carga |
| Progress | `progress` | barra temporal del contador |

## Componentes de dominio (propios)

| Componente | Props clave | Estados/variantes | Vista | Prio |
|-----------|-------------|-------------------|-------|------|
| `LaunchHeader` | title, productName, productRef, unitPrice, currency | — | comprador | P0 |
| `AvailabilityPanel` | availableUnits, reserved, confirmed, status | disponible / por-expirar / agotado | comprador | P0 |
| `StatusBadge` | status (enum dominio) | `RESERVED` `PENDING` `CONFIRMED` `EXPIRED` `SOLD_OUT` | todas | P0 |
| `Countdown` | expiresAt (servidor) | ticking / expirado | comprador | P0 |
| `ReserveForm` | perBuyerLimit, onSubmit | idle / submitting / error | comprador | P0 |
| `ReservationCard` | reservation | activa / expirada / cancelada | comprador | P0 |
| `CheckoutDialog` | reservationId, tokens demo | idle / APPROVE / DECLINE / TIMEOUT | comprador | P0 |
| `ResultBanner` | result | confirmado / pendiente / rechazado / expirado / agotado | comprador | P0 |
| `DegradedBanner` | — | visible cuando el sistema está lento/degradado | todas | P1 |
| `LaunchTable` | launches | vacío / cargando / filas | organizador | P1 |
| `LaunchForm` | launch?, onSubmit | crear / editar / errores por campo | organizador | P1 |
| `StockSummary` | available/reserved/confirmed | invariante visible + gráfico simple | organizador | P1 |
| `ReservationsPanel` | launchId | lista activas con “expira en” informativo | organizador | P1 |
| `EventingStatusCards` | outbox/consumers | OK / fallo | laboratorio | P1 |
| `DltTable` | topic | vacío / filas + enlace a reproceso/manual | laboratorio | P1 |
| `ReconciliationPanel` | orders_failed, stale_reserved | OK / incidencia | laboratorio | P1 |
| `EmptyState` | icon, title, action | reutilizable | todas | P1 |
| `ErrorState` | message, retry | reutilizable | todas | P1 |
| `Money` | amount, currency | formato local | todas | P2 |
| `RelTime` | instant | “en 4 min” / “hace 2 min” | todas | P2 |

## Reglas de implementación

1. **Tokens, no hex.** Todo color/espaciado/radio sale de `tokens.css` mapeado a Tailwind.
2. **Estado ≠ solo color.** `StatusBadge` siempre lleva texto (e ícono opcional).
3. **Accesibilidad**: `Countdown` → `role="status"` `aria-live="polite"`; errores → `role="alert"`
   + inline por campo ([a11y-checklist.md](a11y-checklist.md)).
4. **Táctil**: controles ≥ 44px (`--ec-button-height`, `--ec-input-height`).
5. **Sin lógica de negocio en UI**: el frontend refleja lo que dice la API; no calcula stock ni
   disponibilidad.
6. **shadcn primero**: preferir el primitivo de shadcn y envolverlo; no reinventar diálogos/tablas.
