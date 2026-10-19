package com.eventcart.launch.web;

import com.eventcart.identity.security.AuthenticatedUser;
import com.eventcart.launch.Launch;
import com.eventcart.launch.persistence.LaunchMapper;
import com.eventcart.launch.service.LaunchService;
import com.eventcart.shared.Money;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/launches")
public class LaunchController {

    private final LaunchService launchService;
    private final LaunchMapper mapper;

    public LaunchController(LaunchService launchService, LaunchMapper mapper) {
        this.launchService = launchService;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LaunchResponse create(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @Valid @RequestBody CreateLaunchRequest request) {
        Launch launch = launchService.create(principal.id(), request.title(), request.description(),
                request.productName(), request.productRef(), new Money(request.unitPrice(), request.currency()),
                request.totalStock(), request.opensAt(), request.closesAt(), request.reservationTtlSeconds(),
                request.perBuyerLimit());
        return mapper.toResponse(launch);
    }

    @PatchMapping("/{id}")
    public LaunchResponse reconfigure(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id,
                                      @Valid @RequestBody UpdateLaunchRequest request) {
        Launch launch = launchService.reconfigure(principal.id(), id, request.title(), request.description(),
                request.productName(), request.productRef(), new Money(request.unitPrice(), request.currency()),
                request.opensAt(), request.closesAt(), request.reservationTtlSeconds(), request.perBuyerLimit());
        return mapper.toResponse(launch);
    }

    @PostMapping("/{id}/schedule")
    public LaunchResponse schedule(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return mapper.toResponse(launchService.schedule(principal.id(), id));
    }

    @PostMapping("/{id}/open")
    public LaunchResponse open(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return mapper.toResponse(launchService.open(principal.id(), id));
    }

    @PostMapping("/{id}/close")
    public LaunchResponse close(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return mapper.toResponse(launchService.close(principal.id(), id));
    }

    @PostMapping("/{id}/cancel")
    public LaunchResponse cancel(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return mapper.toResponse(launchService.cancel(principal.id(), id));
    }

    @GetMapping("/mine")
    public List<LaunchResponse> listMine(@AuthenticationPrincipal AuthenticatedUser principal) {
        return launchService.listMine(principal.id()).stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public LaunchResponse get(@PathVariable UUID id) {
        return mapper.toResponse(launchService.get(id));
    }

    @GetMapping
    public List<LaunchResponse> list() {
        return launchService.listPublished().stream().map(mapper::toResponse).toList();
    }
}
