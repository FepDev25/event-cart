package com.eventcart.reservation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, UUID> {

    List<ReservationEntity> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    /**
     * Transiciona a RELEASED solo si sigue en RESERVED (garantiza liberar una sola vez).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE reservations
            SET status = 'RELEASED', updated_at = now()
            WHERE id = :id AND status = 'RESERVED'
            """, nativeQuery = true)
    int releaseIfReserved(@Param("id") UUID id);
}
