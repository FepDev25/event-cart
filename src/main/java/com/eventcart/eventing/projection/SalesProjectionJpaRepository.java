package com.eventcart.eventing.projection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SalesProjectionJpaRepository extends JpaRepository<SalesProjectionEntity, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO sales_projection (launch_id, confirmed_units, updated_at)
            VALUES (:launchId, :units, now())
            ON CONFLICT (launch_id) DO UPDATE
            SET confirmed_units = sales_projection.confirmed_units + :units,
                updated_at      = now()
            """, nativeQuery = true)
    int addConfirmedUnits(@Param("launchId") UUID launchId, @Param("units") long units);
}
