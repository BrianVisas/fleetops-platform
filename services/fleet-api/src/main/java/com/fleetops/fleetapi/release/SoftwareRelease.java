package com.fleetops.fleetapi.release;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "software_release")
public class SoftwareRelease {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String version;

    @Column(name = "target_model", nullable = false, length = 64)
    private String targetModel;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    protected SoftwareRelease() {
    }

    public SoftwareRelease(UUID id, String version, String targetModel, String checksum,
            long sizeBytes, OffsetDateTime publishedAt) {
        this.id = id;
        this.version = version;
        this.targetModel = targetModel;
        this.checksum = checksum;
        this.sizeBytes = sizeBytes;
        this.publishedAt = publishedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public String getTargetModel() {
        return targetModel;
    }

    public String getChecksum() {
        return checksum;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }
}
