package com.fleetops.fleetapi.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateVehicleRequest(

        @NotBlank
        @Size(min = 17, max = 17, message = "a VIN has exactly 17 characters")
        @Pattern(regexp = "[A-HJ-NPR-Z0-9]{17}", message = "a VIN uses digits and capitals without I, O or Q")
        String vin,

        @NotBlank
        @Size(max = 64)
        String model,

        @NotBlank
        @Size(max = 32)
        String swVersion) {
}
