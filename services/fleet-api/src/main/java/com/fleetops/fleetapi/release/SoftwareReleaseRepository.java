package com.fleetops.fleetapi.release;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SoftwareReleaseRepository extends JpaRepository<SoftwareRelease, UUID> {

    boolean existsByVersion(String version);

    List<SoftwareRelease> findAllByOrderByPublishedAtDesc();
}
