package com.eventcart.reservation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface BuyerLaunchUsageJpaRepository extends JpaRepository<BuyerLaunchUsageEntity, BuyerLaunchUsageId> {

    /**
     * Incrementa el uso del comprador solo si respeta el límite. Devuelve 0 filas si lo excede.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO buyer_launch_usage (buyer_id, launch_id, reserved_units, confirmed_units, updated_at)
            VALUES (:buyerId, :launchId, :qty, 0, now())
            ON CONFLICT (buyer_id, launch_id) DO UPDATE
            SET reserved_units = buyer_launch_usage.reserved_units + :qty,
                updated_at     = now()
            WHERE buyer_launch_usage.reserved_units + buyer_launch_usage.confirmed_units + :qty <= :limit
            """, nativeQuery = true)
    int reserveWithinLimit(@Param("buyerId") UUID buyerId, @Param("launchId") UUID launchId, @Param("qty") int qty,
                           @Param("limit") int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE buyer_launch_usage
            SET reserved_units = GREATEST(reserved_units - :qty, 0), updated_at = now()
            WHERE buyer_id = :buyerId AND launch_id = :launchId
            """, nativeQuery = true)
    int release(@Param("buyerId") UUID buyerId, @Param("launchId") UUID launchId, @Param("qty") int qty);
}
