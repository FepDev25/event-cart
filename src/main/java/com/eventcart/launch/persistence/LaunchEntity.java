package com.eventcart.launch.persistence;

import com.eventcart.launch.LaunchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "launches")
@Getter
@Setter
public class LaunchEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_ref")
    private String productRef;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_stock", nullable = false)
    private int totalStock;

    @Column(name = "available_units", nullable = false)
    private int availableUnits;

    @Column(name = "reserved_units", nullable = false)
    private int reservedUnits;

    @Column(name = "confirmed_units", nullable = false)
    private int confirmedUnits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LaunchStatus status;

    @Column(name = "opens_at", nullable = false)
    private Instant opensAt;

    @Column(name = "closes_at", nullable = false)
    private Instant closesAt;

    @Column(name = "reservation_ttl_seconds", nullable = false)
    private int reservationTtlSeconds;

    @Column(name = "per_buyer_limit")
    private Integer perBuyerLimit;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;
}
