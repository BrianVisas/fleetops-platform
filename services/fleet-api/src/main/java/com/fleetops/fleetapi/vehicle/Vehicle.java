package com.fleetops.fleetapi.vehicle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 17)
    private String vin;

    @Column(nullable = false, length = 64)
    private String model;

    @Column(name = "sw_version", nullable = false, length = 32)
    private String swVersion;

    @Column(name = "registered_at", nullable = false)
    private OffsetDateTime registeredAt;

    // JPA needs this; not meant to be used directly.
    protected Vehicle() {
    }

    public Vehicle(UUID id, String vin, String model, String swVersion, OffsetDateTime registeredAt) {
        this.id = id;
        this.vin = vin;
        this.model = model;
        this.swVersion = swVersion;
        this.registeredAt = registeredAt;
    }

    public UUID getId() {
        return id;
    }

    public String getVin() {
        return vin;
    }

    public String getModel() {
        return model;
    }

    public String getSwVersion() {
        return swVersion;
    }

    public OffsetDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setSwVersion(String swVersion) {
        this.swVersion = swVersion;
    }
}
