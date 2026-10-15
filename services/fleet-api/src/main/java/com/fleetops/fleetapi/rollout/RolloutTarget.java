package com.fleetops.fleetapi.rollout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rollout_target")
public class RolloutTarget {

    @Id
    private UUID id;

    @Column(name = "rollout_id", nullable = false)
    private UUID rolloutId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TargetState state;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected RolloutTarget() {
    }

    public RolloutTarget(UUID id, UUID rolloutId, UUID vehicleId, TargetState state, OffsetDateTime updatedAt) {
        this.id = id;
        this.rolloutId = rolloutId;
        this.vehicleId = vehicleId;
        this.state = state;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRolloutId() {
        return rolloutId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public TargetState getState() {
        return state;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setState(TargetState state) {
        this.state = state;
        this.updatedAt = OffsetDateTime.now();
    }
}
