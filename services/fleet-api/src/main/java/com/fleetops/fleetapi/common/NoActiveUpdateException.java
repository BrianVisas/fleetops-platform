package com.fleetops.fleetapi.common;

import java.util.UUID;

public class NoActiveUpdateException extends RuntimeException {

    public NoActiveUpdateException(UUID vehicleId) {
        super("vehicle " + vehicleId + " has no update in progress");
    }
}
