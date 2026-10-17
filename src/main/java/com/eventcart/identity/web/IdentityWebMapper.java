package com.eventcart.identity.web;

import com.eventcart.identity.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IdentityWebMapper {

    UserResponse toResponse(User user);
}
