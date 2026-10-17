package com.eventcart.order.web;

import com.eventcart.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID reservationId,
        UUID launchId,
        int quantity,
        BigDecimal unitPrice,
        String currency,
        BigDecimal totalAmount,
        OrderStatus status,
        String paymentResult,
        String failureReason,
        Instant createdAt) {
}
