package com.eventcart.launch.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdateLaunchRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 200) String productName,
        @Size(max = 200) String productRef,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull Instant opensAt,
        @NotNull Instant closesAt,
        @Positive Integer reservationTtlSeconds,
        @Positive Integer perBuyerLimit) {
}
