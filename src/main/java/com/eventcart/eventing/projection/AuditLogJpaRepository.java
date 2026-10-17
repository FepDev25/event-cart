package com.eventcart.eventing.projection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long> {

    long countByEventId(UUID eventId);
}
