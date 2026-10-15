package com.fleetops.fleetapi.rollout;

import com.fleetops.fleetapi.rollout.dto.ChangePercentageRequest;
import com.fleetops.fleetapi.rollout.dto.CreateRolloutRequest;
import com.fleetops.fleetapi.rollout.dto.RolloutResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rollouts")
public class RolloutController {

    private final RolloutService service;

    public RolloutController(RolloutService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RolloutResponse> create(@Valid @RequestBody CreateRolloutRequest request) {
        Rollout rollout = service.create(request);
        URI location = URI.create("/api/v1/rollouts/" + rollout.getId());
        return ResponseEntity.created(location).body(withCounts(rollout));
    }

    @GetMapping("/{id}")
    public RolloutResponse get(@PathVariable UUID id) {
        return withCounts(service.get(id));
    }

    @PostMapping("/{id}/percentage")
    public RolloutResponse changePercentage(@PathVariable UUID id,
            @Valid @RequestBody ChangePercentageRequest request) {
        return withCounts(service.changePercentage(id, request.percentage()));
    }

    @PostMapping("/{id}/pause")
    public RolloutResponse pause(@PathVariable UUID id) {
        return withCounts(service.pause(id));
    }

    @PostMapping("/{id}/resume")
    public RolloutResponse resume(@PathVariable UUID id) {
        return withCounts(service.resume(id));
    }

    private RolloutResponse withCounts(Rollout rollout) {
        return RolloutResponse.from(
                rollout,
                service.count(rollout.getId(), TargetState.PENDING),
                service.count(rollout.getId(), TargetState.INSTALLING),
                service.count(rollout.getId(), TargetState.SUCCEEDED),
                service.count(rollout.getId(), TargetState.FAILED));
    }
}
