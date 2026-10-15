package com.fleetops.fleetapi.vehicle.dto;

import com.fleetops.fleetapi.vehicle.Vehicle;
import java.time.OffsetDateTime;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        String vin,
        String model,
        String swVersion,
        OffsetDateTime registeredAt) {

    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getVin(),
                vehicle.getModel(),
                vehicle.getSwVersion(),
                vehicle.getRegisteredAt());
    }
}
