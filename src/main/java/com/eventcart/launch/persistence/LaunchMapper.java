package com.eventcart.launch.persistence;

import com.eventcart.launch.Launch;
import com.eventcart.launch.web.LaunchResponse;
import com.eventcart.shared.Money;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.time.Duration;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LaunchMapper {

    @Mapping(source = "unitPrice.amount", target = "unitPrice")
    @Mapping(source = "unitPrice.currency", target = "currency")
    @Mapping(source = "reservationTtl", target = "reservationTtlSeconds")
    LaunchEntity toEntity(Launch launch);

    @Mapping(source = "unitPrice.amount", target = "unitPrice")
    @Mapping(source = "unitPrice.currency", target = "currency")
    @Mapping(source = "reservationTtl", target = "reservationTtlSeconds")
    void updateEntity(Launch launch, @MappingTarget LaunchEntity entity);

    @Mapping(source = "unitPrice.amount", target = "unitPrice")
    @Mapping(source = "unitPrice.currency", target = "currency")
    @Mapping(source = "reservationTtl", target = "reservationTtlSeconds")
    LaunchResponse toResponse(Launch launch);

    default Launch toDomain(LaunchEntity entity) {
        if (entity == null) {
            return null;
        }
        return Launch.reconstitute(
                entity.getId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getProductName(),
                entity.getProductRef(),
                new Money(entity.getUnitPrice(), entity.getCurrency()),
                entity.getTotalStock(),
                entity.getAvailableUnits(),
                entity.getReservedUnits(),
                entity.getConfirmedUnits(),
                entity.getStatus(),
                entity.getOpensAt(),
                entity.getClosesAt(),
                Duration.ofSeconds(entity.getReservationTtlSeconds()),
                entity.getPerBuyerLimit(),
                entity.getSellerId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    default int toSeconds(Duration duration) {
        return (int) duration.getSeconds();
    }
}
