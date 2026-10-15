package com.fleetops.fleetapi.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateVehicleRequest(

        @NotBlank
        @Size(max = 64)
        String model,

        @NotBlank
        @Size(max = 32)
        String swVersion) {
}
