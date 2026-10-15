package com.fleetops.fleetapi.rollout.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ChangePercentageRequest(

        @Min(1)
        @Max(100)
        int percentage) {
}
