package com.eventcart.reservation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "buyer_launch_usage")
@IdClass(BuyerLaunchUsageId.class)
@Getter
@Setter
public class BuyerLaunchUsageEntity {

    @Id
    @Column(name = "buyer_id")
    private UUID buyerId;

    @Id
    @Column(name = "launch_id")
    private UUID launchId;

    @Column(name = "reserved_units", nullable = false)
    private int reservedUnits;

    @Column(name = "confirmed_units", nullable = false)
    private int confirmedUnits;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
