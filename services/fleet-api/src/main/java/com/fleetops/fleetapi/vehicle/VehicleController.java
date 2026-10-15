package com.fleetops.fleetapi.vehicle;

import com.fleetops.fleetapi.vehicle.dto.CreateVehicleRequest;
import com.fleetops.fleetapi.vehicle.dto.UpdateVehicleRequest;
import com.fleetops.fleetapi.vehicle.dto.VehicleResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private final VehicleService service;

    public VehicleController(VehicleService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<VehicleResponse> register(@Valid @RequestBody CreateVehicleRequest request) {
        Vehicle vehicle = service.register(request);
        URI location = URI.create("/api/v1/vehicles/" + vehicle.getId());
        return ResponseEntity.created(location).body(VehicleResponse.from(vehicle));
    }

    @GetMapping
    public Page<VehicleResponse> list(
            @RequestParam(required = false) String model,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.list(model, pageable).map(VehicleResponse::from);
    }

    @GetMapping("/{id}")
    public VehicleResponse get(@PathVariable UUID id) {
        return VehicleResponse.from(service.get(id));
    }

    @PutMapping("/{id}")
    public VehicleResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateVehicleRequest request) {
        return VehicleResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
