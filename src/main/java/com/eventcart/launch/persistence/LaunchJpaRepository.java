package com.eventcart.launch.persistence;

import com.eventcart.launch.LaunchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LaunchJpaRepository extends JpaRepository<LaunchEntity, UUID> {

    List<LaunchEntity> findByStatusInOrderByOpensAtAsc(List<LaunchStatus> statuses);

    /**
     * Captura atómica de stock: reserva unidades solo si el lanzamiento está abierto, dentro de la
     * ventana y hay disponibilidad. Devuelve 0 filas si se rechaza (sin sobreventa).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE launches
            SET available_units = available_units - :qty,
                reserved_units  = reserved_units  + :qty,
                updated_at      = now()
            WHERE id = :launchId
              AND status = 'OPEN'
              AND opens_at <= now() AND now() < closes_at
              AND available_units >= :qty
            """, nativeQuery = true)
    int reserveUnits(@Param("launchId") UUID launchId, @Param("qty") int qty);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE launches
            SET reserved_units  = reserved_units  - :qty,
                available_units = available_units + :qty,
                updated_at      = now()
            WHERE id = :launchId AND reserved_units >= :qty
            """, nativeQuery = true)
    int releaseUnits(@Param("launchId") UUID launchId, @Param("qty") int qty);
}
