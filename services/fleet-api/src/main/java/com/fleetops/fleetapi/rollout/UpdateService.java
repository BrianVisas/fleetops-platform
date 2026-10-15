package com.fleetops.fleetapi.rollout;

import com.fleetops.fleetapi.common.NoActiveUpdateException;
import com.fleetops.fleetapi.common.VehicleNotFoundException;
import com.fleetops.fleetapi.release.SoftwareRelease;
import com.fleetops.fleetapi.release.SoftwareReleaseRepository;
import com.fleetops.fleetapi.rollout.dto.UpdateResultRequest;
import com.fleetops.fleetapi.vehicle.Vehicle;
import com.fleetops.fleetapi.vehicle.VehicleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The vehicle side of a rollout: asking whether an update is assigned, and
 * reporting how the installation went.
 */
@Service
public class UpdateService {

    private static final List<TargetState> OPEN_STATES = List.of(TargetState.PENDING, TargetState.INSTALLING);

    private final RolloutRepository rollouts;
    private final RolloutTargetRepository targets;
    private final VehicleRepository vehicles;
    private final SoftwareReleaseRepository releases;

    public UpdateService(RolloutRepository rollouts, RolloutTargetRepository targets,
            VehicleRepository vehicles, SoftwareReleaseRepository releases) {
        this.rollouts = rollouts;
        this.targets = targets;
        this.vehicles = vehicles;
        this.releases = releases;
    }

    /**
     * Returns the release this vehicle should install, or empty when there is nothing
     * to do - including when the rollout is paused. Handing an update out moves the
     * target to INSTALLING.
     */
    @Transactional
    public Optional<SoftwareRelease> claimUpdate(UUID vehicleId) {
        if (!vehicles.existsById(vehicleId)) {
            throw new VehicleNotFoundException(vehicleId);
        }

        Optional<RolloutTarget> found = targets.findFirstByVehicleIdAndStateIn(vehicleId, OPEN_STATES);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        RolloutTarget target = found.get();
        Rollout rollout = rollouts.findById(target.getRolloutId()).orElseThrow();
        if (rollout.getStatus() != RolloutStatus.RUNNING) {
            return Optional.empty();
        }

        if (target.getState() == TargetState.PENDING) {
            target.setState(TargetState.INSTALLING);
            targets.save(target);
        }

        return releases.findById(rollout.getReleaseId());
    }

    @Transactional
    public void reportResult(UUID vehicleId, UpdateResultRequest request) {
        RolloutTarget target = targets.findFirstByVehicleIdAndStateIn(vehicleId, OPEN_STATES)
                .orElseThrow(() -> new NoActiveUpdateException(vehicleId));

        if (request.success()) {
            target.setState(TargetState.SUCCEEDED);
            Vehicle vehicle = vehicles.findById(vehicleId)
                    .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
            vehicle.setSwVersion(request.version());
            vehicles.save(vehicle);
        } else {
            target.setState(TargetState.FAILED);
        }
        targets.save(target);

        completeRolloutIfFinished(target.getRolloutId());
    }

    private void completeRolloutIfFinished(UUID rolloutId) {
        if (targets.countByRolloutIdAndStateIn(rolloutId, OPEN_STATES) > 0) {
            return;
        }
        rollouts.findById(rolloutId).ifPresent(rollout -> {
            if (rollout.getStatus() == RolloutStatus.RUNNING) {
                rollout.setStatus(RolloutStatus.COMPLETED);
                rollouts.save(rollout);
            }
        });
    }
}
