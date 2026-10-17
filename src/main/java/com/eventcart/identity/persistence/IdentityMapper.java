package com.eventcart.identity.persistence;

import com.eventcart.identity.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IdentityMapper {

    User toDomain(UserEntity entity);
}
