package com.eventcart.checkout.web;

import jakarta.validation.constraints.NotBlank;

public record CheckoutRequest(@NotBlank String paymentToken) {
}
