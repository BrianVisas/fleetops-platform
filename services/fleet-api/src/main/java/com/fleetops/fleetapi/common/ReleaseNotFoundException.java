package com.fleetops.fleetapi.common;

import java.util.UUID;

public class ReleaseNotFoundException extends RuntimeException {

    public ReleaseNotFoundException(UUID id) {
        super("no release with id " + id);
    }
}
