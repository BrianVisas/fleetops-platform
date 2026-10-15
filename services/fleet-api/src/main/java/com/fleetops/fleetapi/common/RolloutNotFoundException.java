package com.fleetops.fleetapi.common;

import java.util.UUID;

public class RolloutNotFoundException extends RuntimeException {

    public RolloutNotFoundException(UUID id) {
        super("no rollout with id " + id);
    }
}
