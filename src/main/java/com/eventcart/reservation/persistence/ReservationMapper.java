package com.eventcart.reservation.persistence;

import com.eventcart.reservation.Reservation;
import com.eventcart.reservation.web.ReservationResponse;
import com.eventcart.shared.Money;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReservationMapper {

    @Mapping(source = "unitPrice.amount", target = "unitPrice")
    @Mapping(source = "unitPrice.currency", target = "currency")
    @Mapping(source = "totalAmount.amount", target = "totalAmount")
    ReservationEntity toEntity(Reservation reservation);

    @Mapping(source = "unitPrice.amount", target = "unitPrice")
    @Mapping(source = "unitPrice.currency", target = "currency")
    @Mapping(source = "totalAmount.amount", target = "totalAmount")
    ReservationResponse toResponse(Reservation reservation);

    default Reservation toDomain(ReservationEntity entity) {
        if (entity == null) {
            return null;
        }
        return Reservation.reconstitute(
                entity.getId(),
                entity.getLaunchId(),
                entity.getBuyerId(),
                entity.getQuantity(),
                new Money(entity.getUnitPrice(), entity.getCurrency()),
                new Money(entity.getTotalAmount(), entity.getCurrency()),
                entity.getStatus(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
