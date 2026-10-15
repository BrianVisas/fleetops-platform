package com.fleetops.fleetapi.vehicle;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    boolean existsByVin(String vin);

    Page<Vehicle> findByModel(String model, Pageable pageable);

    /**
     * Vehicles eligible for a release: right model, not already on that version.
     * Ordered by VIN so the same percentage always picks the same vehicles.
     */
    List<Vehicle> findByModelAndSwVersionNotOrderByVin(String model, String swVersion);
}
