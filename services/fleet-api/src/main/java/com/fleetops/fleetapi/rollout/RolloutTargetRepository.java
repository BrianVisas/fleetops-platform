package com.fleetops.fleetapi.rollout;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolloutTargetRepository extends JpaRepository<RolloutTarget, UUID> {

    boolean existsByRolloutIdAndVehicleId(UUID rolloutId, UUID vehicleId);

    long countByRolloutIdAndState(UUID rolloutId, TargetState state);

    long countByRolloutIdAndStateIn(UUID rolloutId, Collection<TargetState> states);

    Optional<RolloutTarget> findFirstByVehicleIdAndStateIn(UUID vehicleId, Collection<TargetState> states);
}
