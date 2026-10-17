package com.eventcart.reservation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

    Optional<IdempotencyKeyEntity> findByScopeAndIdempotencyKey(String scope, String idempotencyKey);

    /**
     * Registra la clave si no existe. Devuelve 0 si ya existía (incluye claves en curso: espera al commit).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO idempotency_keys (id, scope, idempotency_key, request_hash, created_at, expires_at)
            VALUES (:id, :scope, :key, :requestHash, now(), :expiresAt)
            ON CONFLICT (scope, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("scope") String scope, @Param("key") String key,
                       @Param("requestHash") String requestHash, @Param("expiresAt") Instant expiresAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE idempotency_keys
            SET resource_id = :resourceId, response_status = :status, response_body = :body
            WHERE scope = :scope AND idempotency_key = :key
            """, nativeQuery = true)
    int complete(@Param("scope") String scope, @Param("key") String key, @Param("resourceId") UUID resourceId,
                 @Param("status") int status, @Param("body") String body);
}
