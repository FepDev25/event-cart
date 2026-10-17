package com.eventcart.order.web;

import com.eventcart.identity.security.AuthenticatedUser;
import com.eventcart.order.Order;
import com.eventcart.order.persistence.OrderMapper;
import com.eventcart.order.service.OrderQueryService;
import com.eventcart.shared.NotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderQueryService orderQueryService;
    private final OrderMapper mapper;

    public OrderController(OrderQueryService orderQueryService, OrderMapper mapper) {
        this.orderQueryService = orderQueryService;
        this.mapper = mapper;
    }

    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        Order order = orderQueryService.getOwned(id, principal.id())
                .orElseThrow(() -> new NotFoundException("order not found: " + id));
        return mapper.toResponse(order);
    }

    @GetMapping
    public List<OrderResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return orderQueryService.listMine(principal.id()).stream().map(mapper::toResponse).toList();
    }
}
