package com.fleetops.fleetapi.release.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateReleaseRequest(

        @NotBlank
        @Size(max = 32)
        String version,

        @NotBlank
        @Size(max = 64)
        String targetModel,

        @NotBlank
        @Size(max = 64)
        String checksum,

        @Positive
        long sizeBytes) {
}
