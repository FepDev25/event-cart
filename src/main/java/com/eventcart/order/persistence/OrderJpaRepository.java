package com.eventcart.order.persistence;

import com.eventcart.order.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {

    List<OrderEntity> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    Optional<OrderEntity> findByReservationId(UUID reservationId);

    /**
     * Checkouts {@code PENDING} con resultado indeterminado más antiguo que {@code threshold}, para
     * reconciliación.
     */
    List<OrderEntity> findByStatusAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
            OrderStatus status, Instant threshold, Pageable pageable);

    /**
     * Confirma el pedido solo si sigue PENDING (evita doble procesamiento del checkout).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE orders SET status = 'CONFIRMED', payment_result = :result, updated_at = now()
            WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int confirmIfPending(@Param("id") UUID id, @Param("result") String result);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE orders SET status = 'FAILED', payment_result = :result, failure_reason = :reason, updated_at = now()
            WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int failIfPending(@Param("id") UUID id, @Param("result") String result, @Param("reason") String reason);
}
