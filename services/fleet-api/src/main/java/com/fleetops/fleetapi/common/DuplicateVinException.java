package com.fleetops.fleetapi.common;

public class DuplicateVinException extends RuntimeException {

    public DuplicateVinException(String vin) {
        super("a vehicle with VIN " + vin + " is already registered");
    }
}
