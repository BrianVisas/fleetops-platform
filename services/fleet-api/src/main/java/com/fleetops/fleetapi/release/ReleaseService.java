package com.fleetops.fleetapi.release;

import com.fleetops.fleetapi.common.DuplicateVersionException;
import com.fleetops.fleetapi.common.ReleaseNotFoundException;
import com.fleetops.fleetapi.release.dto.CreateReleaseRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReleaseService {

    private final SoftwareReleaseRepository repository;

    public ReleaseService(SoftwareReleaseRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SoftwareRelease publish(CreateReleaseRequest request) {
        if (repository.existsByVersion(request.version())) {
            throw new DuplicateVersionException(request.version());
        }

        SoftwareRelease release = new SoftwareRelease(
                UUID.randomUUID(),
                request.version(),
                request.targetModel(),
                request.checksum(),
                request.sizeBytes(),
                OffsetDateTime.now());

        try {
            return repository.saveAndFlush(release);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateVersionException(request.version());
        }
    }

    @Transactional(readOnly = true)
    public List<SoftwareRelease> listNewestFirst() {
        return repository.findAllByOrderByPublishedAtDesc();
    }

    @Transactional(readOnly = true)
    public SoftwareRelease get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ReleaseNotFoundException(id));
    }
}
