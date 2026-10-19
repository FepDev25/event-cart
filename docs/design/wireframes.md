# Wireframes (baja fidelidad) — EventCart (M5)

Wireframes de caja, revisables en el PR. No son pixel-perfect: fijan **jerarquía, estados y
contenido**. Mobile-first en la vista comprador; organizador y laboratorio en desktop.

Convenciones: `[ ]` = acción, `( )` = control, `{ }` = badge/estado, `···` = contenido repetible.

---

## 1. Comprador — detalle de lanzamiento (mobile 375)

### 1a. Antes de la apertura
```
┌───────────────────────────────┐
│ ◾ EventCart          (cuenta) │  header
├───────────────────────────────┤
│ [imagen del producto]         │
│                               │
│ Camiseta Oversize “Drop 01”   │  h1 (Outfit)
│ SKU-1 · producto físico       │  caption muted
│                               │
│ {PRÓXIMAMENTE}                │  badge pending/neutral
│ $25.00 USD                    │  precio (destacado)
│                               │
│ Abre en 00:04:12               │  reloj al servidor (informativo)
│ Stock inicial: 100 unidades   │  informativo
│                               │
│ [ Reservar — aún no abre ]    │  CTA deshabilitado
└───────────────────────────────┘
```
- El tiempo hasta apertura se calcula desde `opensAt` del servidor; el navegador **no** habilita la
  compra.

### 1b. Abierto — listo para reservar
```
┌───────────────────────────────┐
│ Camiseta Oversize “Drop 01”   │
│ $25.00 USD                    │
│ {DISPONIBLE}  ·  quedan 100   │  badge available (dato del servidor)
│                               │
│ Cantidad  (−) 1 (+)           │  limitado por perBuyerLimit
│ [        Reservar 1 unidad ]  │  CTA primaria (ink)
└───────────────────────────────┘
```

### 1c. Reserva activa (contador **informativo**)
```
┌───────────────────────────────┐
│ {RESERVADA}  1 unidad         │  badge pending
│ Tienes 09:42 para completar   │  role="status" aria-live=polite
│ ─────────────────────────────  │  progreso temporal (decorativo)
│ Total: $25.00 USD             │
│ [     Completar compra    ]    │  CTA primaria
│ [        Cancelar         ]    │  secundaria
│                               │
│ El stock se libera si el      │  microcopy honesto
│ tiempo termina.               │
└───────────────────────────────┘
```
- Contador = `expires_at` del servidor → **informativo**. Nunca promete stock confirmado.

### 1d. Checkout simulado (dev tokens)
```
┌───────────────────────────────┐
│ Pago simulado                 │
│ (o) APPROVE  (o) DECLINE      │  opciones de demo (solo laboratorio)
│ (o) TIMEOUT                   │
│ [        Pagar          ]      │
└───────────────────────────────┘
```

### 1e. Resultados
```
{CONFIRMADO}  Pago aprobado. Pedido confirmado.        (available, role="status")
{PENDIENTE}   Estamos confirmando tu pago. Te avisaremos. (pending, role="status", 202)
{RECHAZADO}   Pago rechazado. La unidad fue liberada.  (danger, role="alert")
{EXPIRADA}    Tu reserva expiró y liberamos la unidad. (danger, role="status")
{AGOTADO}     Se agotó el stock.                        (danger)
{DEGRADADO}   El sistema está lento; tu solicitud puede demorar. (pending banner)
```

---

## 2. Organizador — lanzamientos (desktop 1024)

### 2a. Lista
```
┌──────────────────────────────────────────────────────────────────┐
│ EventCart · Organizador                        [+ Nuevo lanzamiento]│
├──────────────────────────────────────────────────────────────────┤
│ Título            Estado      Stock   Reservas  Confirmadas  Abre   │
│ ─────────────────────────────────────────────────────────────────  │
│ Drop 01          {ABIERTO}    100     12 (3)     7          12:00  │
│  ·  ·  ·                                                            │
│                                       [Editar] [Abrir/Cerrar] [Ver]  │
└──────────────────────────────────────────────────────────────────┘
```
- `Reservas 12 (3)` = activas (por expirar). Tabla shadcn `<Table>`.

### 2b. Crear / editar
```
┌────────────────────────────┐
│ Título         [·········] │
│ Producto       [·········] │
│ Referencia SKU [·········] │
│ Precio / moneda[25.00][USD]│
│ Stock inicial  [100]       │
│ Abre / Cierra  [fecha/hora]│
│ TTL reserva(s) [600]       │
│ Límite/comprador (opcional)│
│ {estado} · reglas tras abrir venta (solo campos permitidos)
│ [ Guardar ]  [ Publicar ]  │
└────────────────────────────┘
```
- Errores: `FieldError` por campo + resumen enfocable (`role="alert"`).

### 2c. Detalle / inventario
```
┌────────────────────────────────────────────────────┐
│ Drop 01  {ABIERTO}                                  │
│ Disponibles 84 · Reservadas 12 · Confirmadas 7       │  invariante visible
│ [gráfico simple de reservas/ventas]                  │
│ Reservas activas (···) con “expira en” informativo   │
└────────────────────────────────────────────────────┘
```

---

## 3. Laboratorio mínimo (desktop)

```
┌──────────────────────────────────────────────────────────────────┐
│ EventCart · Laboratorio                          (perfil: api/consumer/worker)│
├──────────────────────────────────────────────────────────────────┤
│ Outbox            {OK}  pendientes 0 · publicados 1.204           │
│ Consumidores      notifications {OK} · sales {OK} · audit {OK}    │
│ Duplicados        eventcart.events.duplicates = 3                  │
│ DLQ               eventcart.order.DLT: 1   [ver] (reproceso manual)│
│ Reconciliación    orders_failed 2 · stale_reserved 0              │
│ Reservas expiradas 18                                             │
│ ───────────────────────────────────────────────────────────────── │
│ Enlaces: /actuator/metrics · Kafka UI · scripts/lab                │
└──────────────────────────────────────────────────────────────────┘
```
- El laboratorio **no** reemplaza observabilidad real: enlaza a métricas y a Kafka UI, y a los
  scripts de `scripts/lab/`. Sin simulador de carga en el navegador.

---

## Notas transversales

- **Header** consistente: marca a la izquierda, acción/cuenta a la derecha.
- **Badges de estado** reutilizan el mapping de [design-tokens.md §2](design-tokens.md): available /
  pending / accent / danger.
- **Foco y teclado**: toda interacción navegable; CTA nunca depende del color.
- **Vacío/error**: cada lista y panel define su estado vacío y su estado de error.
