package com.fleetops.fleetapi.rollout;

import com.fleetops.fleetapi.release.SoftwareRelease;
import com.fleetops.fleetapi.rollout.dto.AssignedUpdateResponse;
import com.fleetops.fleetapi.rollout.dto.UpdateResultRequest;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints a vehicle calls for itself. Kept apart from VehicleController, which is
 * the fleet operator's view of the same vehicles.
 */
@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}/update")
public class VehicleUpdateController {

    private final UpdateService service;

    public VehicleUpdateController(UpdateService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<AssignedUpdateResponse> assignedUpdate(@PathVariable UUID vehicleId) {
        Optional<SoftwareRelease> release = service.claimUpdate(vehicleId);
        return release
                .map(value -> ResponseEntity.ok(AssignedUpdateResponse.from(value)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/result")
    public ResponseEntity<Void> reportResult(@PathVariable UUID vehicleId,
            @Valid @RequestBody UpdateResultRequest request) {
        service.reportResult(vehicleId, request);
        return ResponseEntity.accepted().build();
    }
}
