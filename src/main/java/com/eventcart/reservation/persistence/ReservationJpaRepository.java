package com.eventcart.reservation.persistence;

import com.eventcart.reservation.ReservationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, UUID> {

    List<ReservationEntity> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    /**
     * Candidatas a expirar: reservas aún {@code RESERVED} cuyo vencimiento ya pasó, en orden de
     * vencimiento. La selección es solo informativa; el claim real es condicional ({@link #expireIfReserved}).
     */
    List<ReservationEntity> findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            ReservationStatus status, Instant now, Pageable pageable);

    /**
     * Marca {@code EXPIRED} solo si la reserva sigue en {@code RESERVED}. Devuelve 0 filas si ya fue
     * confirmada/liberada, de modo que la liberación de stock ocurre exactamente una vez.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE reservations
            SET status = 'EXPIRED', updated_at = now()
            WHERE id = :id AND status = 'RESERVED'
            """, nativeQuery = true)
    int expireIfReserved(@Param("id") UUID id);

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

    /**
     * Confirma la reserva solo si sigue RESERVED (evita confirmar reservas caducadas/liberadas).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE reservations
            SET status = 'CONFIRMED', updated_at = now()
            WHERE id = :id AND status = 'RESERVED'
            """, nativeQuery = true)
    int confirmIfReserved(@Param("id") UUID id);
}
