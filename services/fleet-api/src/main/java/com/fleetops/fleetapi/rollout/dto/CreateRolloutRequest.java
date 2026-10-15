package com.fleetops.fleetapi.rollout.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateRolloutRequest(

        @NotNull
        UUID releaseId,

        @Min(1)
        @Max(100)
        int percentage) {
}
