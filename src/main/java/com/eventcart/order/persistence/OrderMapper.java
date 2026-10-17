package com.eventcart.order.persistence;

import com.eventcart.order.Order;
import com.eventcart.order.web.OrderResponse;
import com.eventcart.shared.Money;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

    @Mapping(source = "unitPrice.amount", target = "unitPrice")
    @Mapping(source = "unitPrice.currency", target = "currency")
    @Mapping(source = "totalAmount.amount", target = "totalAmount")
    OrderEntity toEntity(Order order);

    @Mapping(source = "unitPrice.amount", target = "unitPrice")
    @Mapping(source = "unitPrice.currency", target = "currency")
    @Mapping(source = "totalAmount.amount", target = "totalAmount")
    OrderResponse toResponse(Order order);

    default Order toDomain(OrderEntity entity) {
        if (entity == null) {
            return null;
        }
        return Order.reconstitute(
                entity.getId(),
                entity.getReservationId(),
                entity.getLaunchId(),
                entity.getBuyerId(),
                entity.getQuantity(),
                new Money(entity.getUnitPrice(), entity.getCurrency()),
                new Money(entity.getTotalAmount(), entity.getCurrency()),
                entity.getStatus(),
                entity.getPaymentResult(),
                entity.getFailureReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
