package com.eventcart.eventing.projection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {

    long countByBuyerIdAndType(UUID buyerId, String type);
}
