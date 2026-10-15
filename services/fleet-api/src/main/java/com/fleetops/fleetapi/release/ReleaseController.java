package com.fleetops.fleetapi.release;

import com.fleetops.fleetapi.release.dto.CreateReleaseRequest;
import com.fleetops.fleetapi.release.dto.ReleaseResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/releases")
public class ReleaseController {

    private final ReleaseService service;

    public ReleaseController(ReleaseService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ReleaseResponse> publish(@Valid @RequestBody CreateReleaseRequest request) {
        SoftwareRelease release = service.publish(request);
        URI location = URI.create("/api/v1/releases/" + release.getId());
        return ResponseEntity.created(location).body(ReleaseResponse.from(release));
    }

    @GetMapping
    public List<ReleaseResponse> list() {
        return service.listNewestFirst().stream().map(ReleaseResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ReleaseResponse get(@PathVariable UUID id) {
        return ReleaseResponse.from(service.get(id));
    }
}
