package com.fleetops.fleetapi.rollout.dto;

import com.fleetops.fleetapi.release.SoftwareRelease;

public record AssignedUpdateResponse(
        String version,
        String checksum,
        long sizeBytes) {

    public static AssignedUpdateResponse from(SoftwareRelease release) {
        return new AssignedUpdateResponse(release.getVersion(), release.getChecksum(), release.getSizeBytes());
    }
}
