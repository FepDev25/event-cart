package com.eventcart.eventing.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxEntity, UUID> {

    List<OutboxEntity> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

    long countByPublishedAtIsNull();
}
