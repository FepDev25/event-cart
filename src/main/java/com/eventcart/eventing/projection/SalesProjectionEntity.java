package com.eventcart.eventing.projection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales_projection")
@Getter
@Setter
public class SalesProjectionEntity {

    @Id
    @Column(name = "launch_id")
    private UUID launchId;

    @Column(name = "confirmed_units", nullable = false)
    private long confirmedUnits;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
