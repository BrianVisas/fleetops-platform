package com.fleetops.fleetapi.common;

public class DuplicateVersionException extends RuntimeException {

    public DuplicateVersionException(String version) {
        super("release version " + version + " is already published");
    }
}
