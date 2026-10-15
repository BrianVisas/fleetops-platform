package com.fleetops.fleetapi.rollout;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolloutRepository extends JpaRepository<Rollout, UUID> {
}
