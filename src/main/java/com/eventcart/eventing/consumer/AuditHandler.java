package com.eventcart.eventing.consumer;

import com.eventcart.eventing.projection.AuditLogEntity;
import com.eventcart.eventing.projection.AuditLogJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Auditoría/operaciones: registro append-only de todos los eventos.
 */
@Service
public class AuditHandler {

    public static final String CONSUMER = "eventcart-audit";

    private final ProcessedEventGuard guard;
    private final AuditLogJpaRepository auditLog;
    private final Clock clock;

    public AuditHandler(ProcessedEventGuard guard, AuditLogJpaRepository auditLog, Clock clock) {
        this.guard = guard;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public void handle(ConsumedEvent event) {
        if (!guard.tryMarkProcessed(CONSUMER, UUID.fromString(event.eventId()))) {
            return;
        }
        AuditLogEntity entry = new AuditLogEntity();
        entry.setEventId(UUID.fromString(event.eventId()));
        entry.setEventType(event.type());
        entry.setAggregateId(UUID.fromString(event.aggregateId()));
        entry.setOccurredAt(event.occurredAt());
        entry.setRecordedAt(clock.instant());
        entry.setPayload(event.payload().toString());
        auditLog.save(entry);
    }
}
