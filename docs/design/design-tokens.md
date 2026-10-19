# Design tokens — EventCart (M5)

Fuente única de verdad en código: [`tokens.css`](tokens.css). Dirección: *marketplace limpio y
confiable*, **solo claro** (decisión del propietario). Arquitectura en **3 capas**:

```
Primitivo (valores crudos)  →  Semántico (propósito)  →  Componente (uso concreto)
--ec-neutral-900            →  --ec-color-primary      →  --ec-button-primary-bg
```

**Regla dura:** los componentes (M6) **nunca** usan hex crudo ni primitivos; solo tokens semánticos o
de componente. Los primitivos existen para construir la capa semántica.

## 1. Primitivos

| Grupo | Tokens | Uso |
|-------|--------|-----|
| Neutral | `--ec-neutral-0…900` (slate) | superficies, texto, bordes |
| Naranja | `--ec-orange-50/100/500/600/700` | **accent de escasez** (expirado-por-vencer, "limitado") |
| Verde | `--ec-green-50/500/600/700` | disponible / confirmado / éxito |
| Ámbar | `--ec-amber-50/100/500/700` | pendiente / por expirar (aviso) |
| Rojo | `--ec-red-50/100/600/700` | error / agotado / destructivo |

## 2. Semánticos

| Token | Valor (hex) | Rol |
|-------|-------------|-----|
| `--ec-color-background` | `#F8FAFC` | fondo de página |
| `--ec-color-surface` | `#FFFFFF` | tarjetas / superficies |
| `--ec-color-foreground` | `#0F172A` | texto principal |
| `--ec-color-muted-foreground` | `#475569` | texto secundario |
| `--ec-color-border` | `#E2E8F0` | separadores decorativos |
| `--ec-color-primary` | `#0F172A` | acción primaria (ink) |
| `--ec-color-accent` | `#EA580C` | badge de escasez (texto ink) |
| `--ec-color-accent-strong` | `#C2410C` | botón de acento relleno (texto blanco) |
| `--ec-color-available` | `#047857` | "disponible / confirmado" |
| `--ec-color-pending` | `#B45309` | "pendiente / por expirar" |
| `--ec-color-danger` | `#DC2626` | "agotado / error" |
| `--ec-color-ring` | `#0F172A` | anillo de foco |

Semántica de estado del dominio → color (mapping explícito, evita ambigüedad):

| Estado (dominio) | Color | Surface |
|------------------|-------|---------|
| `available` (unidades libres) | `--ec-color-available` | `--ec-color-available-surface` |
| `reserved` (activa, informativo) | `--ec-color-pending` | `--ec-color-pending-surface` |
| `confirmed` (vendido) | `--ec-color-available` | `--ec-color-available-surface` |
| `expiring` (por vencer) | `--ec-color-accent` | `--ec-color-accent-surface` |
| `expired` / agotado | `--ec-color-danger` | `--ec-color-danger-surface` |

> El **contador de caducidad es informativo**: usa `--ec-color-pending`/`--ec-color-accent`, nunca
> implica stock confirmado. Ver [wireframes.md](wireframes.md).

## 3. Tipografía

| Token | Valor |
|-------|-------|
| `--ec-font-heading` | **Outfit** (geometric modern, títulos) |
| `--ec-font-body` | **Work Sans** (cuerpo, alta legibilidad) |
| `--ec-text-display-size` | 40px / 1.1 |
| `--ec-text-h1-size` | 32px / 1.2 |
| `--ec-text-h2-size` | 24px / 1.25 |
| `--ec-text-h3-size` | 20px / 1.3 |
| `--ec-text-body-size` | 16px / 1.6 |
| `--ec-text-small-size` | 14px / 1.5 |
| `--ec-text-caption-size` | 12px / 1.4 |

Import (M6, `index.html` o CSS):
```css
@import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700&family=Work+Sans:wght@400;500;600&display=swap');
```

## 4. Espaciado, radios, sombras, movimiento

- **Espaciado (ritmo 4/8):** `--ec-space-1…8` = 4, 8, 12, 16, 24, 32, 48, 64 px.
- **Radios:** `sm 6` · `md 10` · `lg 14` · `pill 999`.
- **Sombras:** `--ec-shadow-sm` (reposo) · `--ec-shadow-md` (elevado).
- **Movimiento:** `fast 150ms` · `base 200ms` · `slow 300ms`, easing `standard`; se anulan con
  `prefers-reduced-motion: reduce` (ya incluido en `tokens.css`).

## 5. Contraste (WCAG 2.2 AA)

| Combinación | Ratio | Requisito | OK |
|-------------|-------|-----------|----|
| `foreground` sobre surface | ≈ 17.9:1 | 4.5 (texto) | ✅ |
| `muted-foreground` sobre surface | ≈ 7.6:1 | 4.5 | ✅ |
| blanco sobre `primary` (ink) | ≈ 17.9:1 | 4.5 | ✅ |
| ink sobre `accent` (#EA580C) | ≈ 5.1:1 | 4.5 | ✅ |
| blanco sobre `accent-strong` (#C2410C) | ≈ 5.2:1 | 4.5 | ✅ |
| `available` (#047857) sobre surface | ≈ 5.5:1 | 4.5 | ✅ |
| `pending` (#B45309) sobre surface | ≈ 5.0:1 | 4.5 | ✅ |
| `danger` (#DC2626) sobre surface | ≈ 4.8:1 | 4.5 | ✅ |
| `input-border` (#64748B) sobre surface | ≈ 4.8:1 | 3.0 (1.4.11) | ✅ |

> Nota: los **separadores decorativos** (`--ec-color-border`) no alcanzan 3:1 y están permitidos
> porque no identifican controles ni transmiten información; los **límites de control** usan
> `--ec-input-border` (≥ 3:1).

## 6. Integración con shadcn/ui (M6)

En M6 se mapean estos tokens a las variables de shadcn/Tailwind en `:root` (una sola vez):

| shadcn | Origen EventCart |
|--------|------------------|
| `--background` | `--ec-color-background` |
| `--foreground` | `--ec-color-foreground` |
| `--card` / `--card-foreground` | `--ec-color-surface` / `--ec-color-foreground` |
| `--primary` / `--primary-foreground` | `--ec-color-primary` / `--ec-color-primary-foreground` |
| `--muted` / `--muted-foreground` | `--ec-color-surface-muted` / `--ec-color-muted-foreground` |
| `--border` / `--input` / `--ring` | `--ec-color-border` / `--ec-input-border` / `--ec-color-ring` |
| `--destructive` / `--destructive-foreground` | `--ec-color-danger` / `--ec-color-danger-foreground` |
| `--radius` | `--ec-radius-md` |

Los estados de dominio (`available`/`pending`/`accent`) no tienen equivalente nativo en shadcn: se
exponen como utilidades propias (p. ej. `bg-available-surface text-available`) desde los tokens.

## 7. Anti-patrones (no hacer)

- Hex crudo en componentes, o saltarse la capa semántica.
- Usar el naranja de acento como color de éxito o de marca genérica (rompe la semántica de escasez).
- Fondo menta/verde tintado de página (se descartó: menos "limpio/neutro").
- Texto blanco sobre `--ec-color-accent` (#EA580C) → 3.5:1, **no** cumple para texto normal.
- Transmitir estado solo por color (siempre acompañar con texto/ícono).
