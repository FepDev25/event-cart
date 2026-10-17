package com.eventcart.launch.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.eventcart.launch.LaunchStatus;

public record LaunchResponse(
        UUID id,
        String title,
        String description,
        String productName,
        String productRef,
        BigDecimal unitPrice,
        String currency,
        int totalStock,
        int availableUnits,
        int reservedUnits,
        int confirmedUnits,
        LaunchStatus status,
        Instant opensAt,
        Instant closesAt,
        int reservationTtlSeconds,
        Integer perBuyerLimit,
        UUID sellerId) {
}
