package com.eventcart.reservation.persistence;

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
     * Candidatas a expirar: reservas aún {@code RESERVED} cuyo vencimiento ya pasó y que <em>no</em>
     * tienen un checkout {@code PENDING} dentro de la ventana de gracia (protege pagos en vuelo). La
     * selección es solo informativa; el claim real es condicional ({@link #expireIfReserved}).
     */
    @Query(value = """
            SELECT * FROM reservations r
            WHERE r.status = 'RESERVED'
              AND r.expires_at <= :now
              AND NOT EXISTS (
                  SELECT 1 FROM orders o
                  WHERE o.reservation_id = r.id
                    AND o.status = 'PENDING'
                    AND o.created_at > :graceFloor
              )
            ORDER BY r.expires_at
            LIMIT :batchSize
            """, nativeQuery = true)
    List<ReservationEntity> findExpirable(@Param("now") Instant now, @Param("graceFloor") Instant graceFloor,
                                          @Param("batchSize") int batchSize);

    /**
     * Reservas que ya deberían haber expirado (más antigua que la ventana de gracia y sin checkout
     * fresco): señal de inconsistencia si el barrido de expiración va retrasado.
     */
    @Query(value = """
            SELECT count(*) FROM reservations r
            WHERE r.status = 'RESERVED'
              AND r.expires_at <= :expiredBefore
              AND NOT EXISTS (
                  SELECT 1 FROM orders o
                  WHERE o.reservation_id = r.id
                    AND o.status = 'PENDING'
                    AND o.created_at > :pendingSince
              )
            """, nativeQuery = true)
    long countExpirable(@Param("expiredBefore") Instant expiredBefore, @Param("pendingSince") Instant pendingSince);

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
