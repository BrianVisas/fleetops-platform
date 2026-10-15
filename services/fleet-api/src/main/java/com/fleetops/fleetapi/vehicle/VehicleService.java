package com.fleetops.fleetapi.vehicle;

import com.fleetops.fleetapi.common.DuplicateVinException;
import com.fleetops.fleetapi.common.VehicleNotFoundException;
import com.fleetops.fleetapi.vehicle.dto.CreateVehicleRequest;
import com.fleetops.fleetapi.vehicle.dto.UpdateVehicleRequest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

    private final VehicleRepository repository;

    public VehicleService(VehicleRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Vehicle register(CreateVehicleRequest request) {
        if (repository.existsByVin(request.vin())) {
            throw new DuplicateVinException(request.vin());
        }

        Vehicle vehicle = new Vehicle(
                UUID.randomUUID(),
                request.vin(),
                request.model(),
                request.swVersion(),
                OffsetDateTime.now());

        try {
            // saveAndFlush so a unique violation surfaces here and not at commit time.
            return repository.saveAndFlush(vehicle);
        } catch (DataIntegrityViolationException e) {
            // Two requests with the same VIN can pass the check above at the same time.
            // The unique index is what actually prevents the duplicate.
            throw new DuplicateVinException(request.vin());
        }
    }

    @Transactional(readOnly = true)
    public Page<Vehicle> list(String model, Pageable pageable) {
        if (model == null || model.isBlank()) {
            return repository.findAll(pageable);
        }
        return repository.findByModel(model, pageable);
    }

    @Transactional(readOnly = true)
    public Vehicle get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new VehicleNotFoundException(id));
    }

    @Transactional
    public Vehicle update(UUID id, UpdateVehicleRequest request) {
        Vehicle vehicle = get(id);
        vehicle.setModel(request.model());
        vehicle.setSwVersion(request.swVersion());
        return repository.save(vehicle);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new VehicleNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
