package com.eventcart.order.service;

import com.eventcart.order.Order;
import com.eventcart.order.persistence.OrderJpaRepository;
import com.eventcart.order.persistence.OrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderQueryService {

    private final OrderJpaRepository orders;
    private final OrderMapper mapper;

    public OrderQueryService(OrderJpaRepository orders, OrderMapper mapper) {
        this.orders = orders;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOwned(UUID orderId, UUID buyerId) {
        return orders.findById(orderId)
                .filter(order -> order.getBuyerId().equals(buyerId))
                .map(mapper::toDomain);
    }

    @Transactional(readOnly = true)
    public List<Order> listMine(UUID buyerId) {
        return orders.findByBuyerIdOrderByCreatedAtDesc(buyerId).stream().map(mapper::toDomain).toList();
    }
}
