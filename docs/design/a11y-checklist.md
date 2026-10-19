# Checklist de accesibilidad por vista (M5 → verificar en M6)

Objetivo: **WCAG 2.2 AA** + mobile-first. Se marca en el PR de M6 por vista. Base en
[`design-tokens.md`](design-tokens.md) y [`components.md`](components.md).

## Transversal (todas las vistas)

- [ ] Contraste de texto ≥ **4.5:1** (normal) y ≥ 3:1 (grande) sobre su superficie.
- [ ] Límites de controles (inputs, botones outline) ≥ **3:1** vs. fondo adyacente.
- [ ] Foco **visible** en todo elemento interactivo (`:focus-visible`, anillo `--ec-color-ring`).
- [ ] Orden de foco = orden visual; sin trampas de foco.
- [ ] Estado **no** comunicado solo por color (siempre texto/ícono).
- [ ] Objetivos táctiles ≥ **44×44px** (`--ec-button-height`, `--ec-input-height`).
- [ ] `prefers-reduced-motion` respetado (duraciones a 0 ms; sin animaciones esenciales).
- [ ] Zoom 200% sin pérdida de contenido ni scroll horizontal.
- [ ] Probado a **375 / 768 / 1024 / 1440**.
- [ ] Iconografía vectorial consistente; íconos decorativos con `aria-hidden="true"`.
- [ ] Idioma del documento declarado (`lang="es"`).

## Comprador

- [ ] **Countdown** informativo con `role="status"` `aria-live="polite"`; anuncia estado
      significativo ("Tienes 9 min para completar"), **no** un número desnudo.
- [ ] **Stock/estado** proviene del servidor; el texto lo deja claro (no implica confirmación).
- [ ] **CTA de reserva** con nombre accesible; deshabilitado antes de abrir con explicación visible
      ("Aún no abre").
- [ ] Selección de **cantidad** operable por teclado y lector de pantalla (`label`, rango válido).
- [ ] **Checkout**: errores con `role="alert"` + mensaje inline por campo; resumen enfocable si hay
      varios errores.
- [ ] **Resultados** (confirmado/pendiente/rechazado/expirado/agotado) anunciados por estado, con
      acción siguiente clara.
- [ ] Estado **agotado/cerrado**: CTA deshabilitado y mensaje explícito.

## Organizador

- [ ] **Tabla** con `<th>` semánticos y `scope`; navegable por teclado.
- [ ] **Formulario**: `label` por campo, `aria-describedby` para ayudas/errores, `FieldError`
      asociado; foco al resumen de errores tras fallo.
- [ ] Acciones destructivas (cerrar/cancelar) con confirmación (`AlertDialog`) y nombre accesible.
- [ ] Métricas/estado con texto además de números; no solo color.
- [ ] Reglas tras abrir venta: campos no editables con estado `disabled` **y** motivo visible.

## Laboratorio

- [ ] Indicadores (`OK`/fallo) con texto, no solo verde/rojo.
- [ ] **DLQ**: tabla accesible; enlace de reproceso con advertencia y confirmación.
- [ ] Datos largos (payloads) en `<pre>` con `tabindex`/scroll accesible; sin truncados que oculten
      información crítica sin alternativa.
- [ ] Enlaces externos (Kafka UI, `/actuator/metrics`) con texto descriptivo.
- [ ] Refresco periódico sin robar foco ni re-anunciar en bucle (pausar `aria-live` en updates
      ruidosos).
