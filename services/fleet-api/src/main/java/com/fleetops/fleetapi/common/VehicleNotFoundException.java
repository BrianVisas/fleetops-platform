package com.fleetops.fleetapi.common;

import java.util.UUID;

public class VehicleNotFoundException extends RuntimeException {

    public VehicleNotFoundException(UUID id) {
        super("no vehicle with id " + id);
    }
}
