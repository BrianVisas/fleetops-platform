package com.fleetops.fleetapi.rollout.dto;

import com.fleetops.fleetapi.rollout.Rollout;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RolloutResponse(
        UUID id,
        UUID releaseId,
        String status,
        int percentage,
        long pending,
        long installing,
        long succeeded,
        long failed,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static RolloutResponse from(Rollout rollout, long pending, long installing,
            long succeeded, long failed) {
        return new RolloutResponse(
                rollout.getId(),
                rollout.getReleaseId(),
                rollout.getStatus().name(),
                rollout.getPercentage(),
                pending,
                installing,
                succeeded,
                failed,
                rollout.getCreatedAt(),
                rollout.getUpdatedAt());
    }
}
