package com.fleetops.fleetapi.rollout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateResultRequest(

        @NotBlank
        @Size(max = 32)
        String version,

        boolean success) {
}
