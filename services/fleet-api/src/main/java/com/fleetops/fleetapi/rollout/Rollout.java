package com.fleetops.fleetapi.rollout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A staged rollout of one release. Foreign keys are kept as plain ids rather than
 * JPA relations: the service loads what it needs explicitly, which keeps the
 * queries visible instead of hidden behind lazy loading.
 */
@Entity
@Table(name = "rollout")
public class Rollout {

    @Id
    private UUID id;

    @Column(name = "release_id", nullable = false)
    private UUID releaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RolloutStatus status;

    @Column(nullable = false)
    private int percentage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Rollout() {
    }

    public Rollout(UUID id, UUID releaseId, RolloutStatus status, int percentage,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.releaseId = releaseId;
        this.status = status;
        this.percentage = percentage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReleaseId() {
        return releaseId;
    }

    public RolloutStatus getStatus() {
        return status;
    }

    public int getPercentage() {
        return percentage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(RolloutStatus status) {
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
    }

    public void setPercentage(int percentage) {
        this.percentage = percentage;
        this.updatedAt = OffsetDateTime.now();
    }
}
