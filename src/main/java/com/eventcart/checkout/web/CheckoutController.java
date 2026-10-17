package com.eventcart.checkout.web;

import com.eventcart.checkout.service.CheckoutService;
import com.eventcart.identity.security.AuthenticatedUser;
import com.eventcart.order.Order;
import com.eventcart.order.OrderStatus;
import com.eventcart.order.persistence.OrderMapper;
import com.eventcart.order.web.OrderResponse;
import com.eventcart.shared.MissingIdempotencyKeyException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final OrderMapper orderMapper;

    public CheckoutController(CheckoutService checkoutService, OrderMapper orderMapper) {
        this.checkoutService = checkoutService;
        this.orderMapper = orderMapper;
    }

    @PostMapping("/reservations/{id}/checkout")
    public ResponseEntity<OrderResponse> checkout(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @PathVariable UUID id,
                                                  @RequestHeader(name = "Idempotency-Key", required = false)
                                                  String idempotencyKey,
                                                  @Valid @RequestBody CheckoutRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException("Idempotency-Key header is required");
        }
        Order order = checkoutService.checkout(id, principal.id(), request.paymentToken(), idempotencyKey);
        HttpStatus status = order.getStatus() == OrderStatus.PENDING ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(orderMapper.toResponse(order));
    }
}
