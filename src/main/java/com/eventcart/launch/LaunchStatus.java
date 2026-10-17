package com.eventcart.launch;

/**
 * Ciclo de vida de un lanzamiento.
 *
 * <pre>
 * DRAFT ─▶ SCHEDULED ─▶ OPEN ─▶ CLOSED
 *   └──────────┴──────────┴────▶ CANCELLED
 * </pre>
 */
public enum LaunchStatus {
    DRAFT,
    SCHEDULED,
    OPEN,
    CLOSED,
    CANCELLED;

    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }
}
