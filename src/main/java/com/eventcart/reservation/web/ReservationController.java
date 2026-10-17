package com.eventcart.reservation.web;

import com.eventcart.identity.security.AuthenticatedUser;
import com.eventcart.reservation.persistence.ReservationMapper;
import com.eventcart.reservation.service.ReservationApplicationService;
import com.eventcart.shared.MissingIdempotencyKeyException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ReservationController {

    private final ReservationApplicationService reservationService;
    private final ReservationMapper mapper;

    public ReservationController(ReservationApplicationService reservationService, ReservationMapper mapper) {
        this.reservationService = reservationService;
        this.mapper = mapper;
    }

    @PostMapping("/launches/{launchId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable UUID launchId,
                                       @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                       @Valid @RequestBody CreateReservationRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException("Idempotency-Key header is required");
        }
        return mapper.toResponse(
                reservationService.reserve(launchId, principal.id(), request.quantity(), idempotencyKey));
    }

    @GetMapping("/reservations/{id}")
    public ReservationResponse get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return mapper.toResponse(reservationService.get(id, principal.id()));
    }

    @GetMapping("/reservations")
    public List<ReservationResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return reservationService.listMine(principal.id()).stream().map(mapper::toResponse).toList();
    }

    @PostMapping("/reservations/{id}/cancel")
    public ReservationResponse cancel(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return mapper.toResponse(reservationService.cancel(id, principal.id()));
    }
}
