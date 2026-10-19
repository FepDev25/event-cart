# Design brief — EventCart (M5)

> Dirección visual y sistema de diseño para construir el frontend con criterio profesional **antes**
> de escribir componentes de producto (M6). Sin lógica de negocio ni integración con la API.

## 1. Producto y público

EventCart es un **motor de lanzamientos programados** de productos físicos de disponibilidad
limitada. Tres audiencias y sus necesidades emocionales:

| Vista | Quién | Qué necesita sentir |
|-------|-------|---------------------|
| **Comprador** | persona en el móvil, en el minuto de apertura | "sé exactamente qué pasa y en cuánto tiempo; no me van a engañar" |
| **Organizador** | quien publica y vende | "tengo el control y veo números reales" |
| **Laboratorio** | operador técnico (dev) | "veo el estado del sistema, incidencias y consistencia" |

## 2. Dirección visual

**"Marketplace limpio y confiable, con un solo acento de escasez."**

- **Superficie neutra** (blanco/gris muy claro) y **tipografía geométrica y legible**
  → transmite limpieza y confianza, no retail estridente.
- **Primario ink** (casi negro) para la acción principal → serio, de marca, sin depender de un
  color "corporativo" genérico.
- **Un único acento naranja** reservado para la **escasez real** (limitado / por expirar). La energía
  del "drop" la lleva el acento, **no** el fondo ni animaciones agresivas.
- **Color de estado honesto:** verde = disponible/confirmado, ámbar = pendiente/por expirar, rojo =
  agotado/error. Nunca se usa color decorativamente para fingir urgencia.

Justificación (de la base de recomendaciones de diseño UI/UX, no de gusto personal): el par
tipográfico *geometric modern* (Outfit + Work Sans) puntúa alto en claridad para producto; el enfoque
de "base neutra + acento semántico" evita los sesgos de las paletas retail (fondo menta, CTA verde)
que se sienten menos neutras/confiables.

## 3. Principios

1. **Honestidad de estado.** La UI refleja el estado real del dominio (`RESERVED`, `PENDING`,
   `CONFIRMED`, `EXPIRED`). El **contador de caducidad es informativo**; nunca se infiere stock
   confirmado desde el cliente.
2. **Una acción dominante por pantalla.** El CTA de reserva es inconfundible; lo demás es
   informativo.
3. **Claridad antes que ingenio.** Microcopy explícito sobre qué acaba de pasar y qué sigue.
4. **Accesibilidad por defecto** (WCAG 2.2 AA): foco visible, contraste ≥ 4.5:1 en texto, objetivos
   táctiles ≥ 44px, estado anunciado por texto y no solo por color.
5. **Mobile-first.** El comprador reserva desde el teléfono durante la apertura.
6. **Tokens, no hex.** Toda decisión visual vive en [`tokens.css`](tokens.css).

## 4. Anti-patrones (prohibido)

- **Falsas señales de escasez**: "¡solo 2 disponibles!" inventado, contadores que se reinician,
  "X personas viendo esto". Viola AGENTS y destruye la confianza.
- **Countdown como fuente de verdad**: el cliente nunca decide stock ni disponibilidad.
- Presión oscura: cuentas atrás que no corresponden a `expires_at` real; botones que esquivan.
- Estado solo por color (semáforo sin texto/ícono).
- Emojis como iconografía estructural (usar vectores consistentes, p. ej. Lucide/Phosphor).
- Copiar a Ticketmaster/Shopify como identidad; el objetivo es parecer **propio y honesto**.

## 5. Voz y tono (es)

- Directo, sereno, sin exclamaciones ni FOMO.
- Explica el "qué" y el "porqué": *"Reserva 1 unidad. Tienes 10 min para completar la compra."*
- Estados terminales claros: *"Tu reserva expiró y liberamos la unidad."* / *"Pago aprobado.
  Pedido confirmado."* / *"Estamos confirmando tu pago. Te avisaremos."*
- Nunca prometer algo que el backend no confirmó.

## 6. Accesibilidad y responsive (requisito, no extra)

- Breakpoints de prueba: **375 / 768 / 1024 / 1440**.
- Foco visible (`:focus-visible`, anillo `--ec-color-ring`).
- Anuncios de estado con `role="status"` / `role="alert"`.
- Checklist por vista en [a11y-checklist.md](a11y-checklist.md).

## 7. Referencias de inspiración (categorías, no copia)

- **E-commerce de producto premium**: foco en la pieza, tipografía contenida, un acento.
- **Dashboards SaaS limpios** (organizador/laboratorio): tablas legibles, densidad moderada, estados
  con badge.
- **Sistemas accesibles con tokens** (patrón primitivo→semántico→componente).

## 8. Entregables de M5

| Artefacto | Archivo |
|-----------|---------|
| Este brief | `docs/design/design-brief.md` |
| Tokens + variables CSS | `docs/design/design-tokens.md` · `tokens.css` |
| Wireframes (3 vistas, lo-fi) | `docs/design/wireframes.md` |
| Catálogo de componentes (entrada M6) | `docs/design/components.md` |
| Prototipo de 1 pantalla | `docs/design/prototype/reservation.html` |
| Checklist de accesibilidad | `docs/design/a11y-checklist.md` |

**Criterio de aceptación (roadmap):** brief + tokens + wireframes de las 3 vistas + prototipo de 1
pantalla aprobados por el propietario, y la lista de componentes para M6.

## 9. Fuera de alcance

- Sin lógica de negocio ni llamadas reales a la API (el prototipo es solo UI).
- Sin tema oscuro (decidido: solo claro).
- Sin Figma (el repo es la fuente de verdad).

## 10. Decisiones registradas (fase M5)

| Decisión | Elección |
|----------|----------|
| Dirección visual | Marketplace limpio y confiable |
| Stack frontend (M6) | Vite + React + TS + shadcn/ui + Tailwind + TanStack Query |
| Tema | Solo claro |
| Fidelidad wireframes | Lo-fi en markdown + 1 prototipo HTML |
| Pantalla del prototipo | Reserva del comprador |
| Figma | No; diseño en el repo |
| Paleta + tipografía | Base neutra + acento naranja · Outfit + Work Sans |
