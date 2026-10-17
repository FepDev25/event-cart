package com.eventcart.reservation.web;

import jakarta.validation.constraints.Positive;

public record CreateReservationRequest(@Positive int quantity) {
}
