package com.fleetops.fleetapi.release.dto;

import com.fleetops.fleetapi.release.SoftwareRelease;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReleaseResponse(
        UUID id,
        String version,
        String targetModel,
        String checksum,
        long sizeBytes,
        OffsetDateTime publishedAt) {

    public static ReleaseResponse from(SoftwareRelease release) {
        return new ReleaseResponse(
                release.getId(),
                release.getVersion(),
                release.getTargetModel(),
                release.getChecksum(),
                release.getSizeBytes(),
                release.getPublishedAt());
    }
}
