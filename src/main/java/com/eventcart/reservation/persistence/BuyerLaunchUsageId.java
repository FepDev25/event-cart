package com.eventcart.reservation.persistence;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class BuyerLaunchUsageId implements Serializable {

    private UUID buyerId;
    private UUID launchId;
}
