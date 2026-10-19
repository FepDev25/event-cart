package com.eventcart.launch.service;

import com.eventcart.launch.Launch;
import com.eventcart.launch.LaunchStatus;
import com.eventcart.launch.persistence.LaunchEntity;
import com.eventcart.launch.persistence.LaunchJpaRepository;
import com.eventcart.launch.persistence.LaunchMapper;
import com.eventcart.shared.ForbiddenException;
import com.eventcart.shared.Money;
import com.eventcart.shared.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class LaunchService {

    private static final int DEFAULT_RESERVATION_TTL_SECONDS = 600;

    private final LaunchJpaRepository launches;
    private final LaunchMapper mapper;
    private final Clock clock;

    public LaunchService(LaunchJpaRepository launches, LaunchMapper mapper, Clock clock) {
        this.launches = launches;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public Launch create(UUID sellerId, String title, String description, String productName, String productRef,
                         Money unitPrice, int totalStock, Instant opensAt, Instant closesAt,
                         Integer reservationTtlSeconds, Integer perBuyerLimit) {
        Launch launch = Launch.create(title, description, productName, productRef, unitPrice, totalStock, opensAt,
                closesAt, ttl(reservationTtlSeconds), perBuyerLimit, sellerId, clock.instant());
        LaunchEntity entity = mapper.toEntity(launch);
        launches.save(entity);
        return mapper.toDomain(entity);
    }

    @Transactional
    public Launch reconfigure(UUID requesterId, UUID launchId, String title, String description, String productName,
                              String productRef, Money unitPrice, Instant opensAt, Instant closesAt,
                              Integer reservationTtlSeconds, Integer perBuyerLimit) {
        Launch launch = loadOwned(requesterId, launchId);
        launch.reconfigure(title, description, productName, productRef, unitPrice, opensAt, closesAt,
                ttl(reservationTtlSeconds), perBuyerLimit, clock.instant());
        return save(launch);
    }

    @Transactional
    public Launch schedule(UUID requesterId, UUID launchId) {
        Launch launch = loadOwned(requesterId, launchId);
        launch.schedule(clock.instant());
        return save(launch);
    }

    @Transactional
    public Launch open(UUID requesterId, UUID launchId) {
        Launch launch = loadOwned(requesterId, launchId);
        launch.open(clock.instant());
        return save(launch);
    }

    @Transactional
    public Launch close(UUID requesterId, UUID launchId) {
        Launch launch = loadOwned(requesterId, launchId);
        launch.close(clock.instant());
        return save(launch);
    }

    @Transactional
    public Launch cancel(UUID requesterId, UUID launchId) {
        Launch launch = loadOwned(requesterId, launchId);
        launch.cancel(clock.instant());
        return save(launch);
    }

    @Transactional(readOnly = true)
    public Launch get(UUID launchId) {
        return mapper.toDomain(load(launchId));
    }

    @Transactional(readOnly = true)
    public List<Launch> listPublished() {
        return launches.findByStatusInOrderByOpensAtAsc(
                        List.of(LaunchStatus.SCHEDULED, LaunchStatus.OPEN, LaunchStatus.CLOSED))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Launch> listMine(UUID sellerId) {
        return launches.findBySellerIdOrderByCreatedAtDesc(sellerId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    private LaunchEntity load(UUID launchId) {
        return launches.findById(launchId)
                .orElseThrow(() -> new NotFoundException("launch not found: " + launchId));
    }

    private Launch loadOwned(UUID requesterId, UUID launchId) {
        Launch launch = mapper.toDomain(load(launchId));
        if (!launch.getSellerId().equals(requesterId)) {
            throw new ForbiddenException("user " + requesterId + " does not own launch " + launchId);
        }
        return launch;
    }

    private Launch save(Launch launch) {
        LaunchEntity entity = load(launch.getId());
        mapper.updateEntity(launch, entity);
        launches.save(entity);
        return mapper.toDomain(entity);
    }

    private static Duration ttl(Integer seconds) {
        return Duration.ofSeconds(seconds == null ? DEFAULT_RESERVATION_TTL_SECONDS : seconds);
    }
}
