package com.fleetops.fleetapi.rollout;

import com.fleetops.fleetapi.common.RolloutNotFoundException;
import com.fleetops.fleetapi.release.ReleaseService;
import com.fleetops.fleetapi.release.SoftwareRelease;
import com.fleetops.fleetapi.rollout.dto.CreateRolloutRequest;
import com.fleetops.fleetapi.vehicle.Vehicle;
import com.fleetops.fleetapi.vehicle.VehicleRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RolloutService {

    private final RolloutRepository rollouts;
    private final RolloutTargetRepository targets;
    private final VehicleRepository vehicles;
    private final ReleaseService releases;

    public RolloutService(RolloutRepository rollouts, RolloutTargetRepository targets,
            VehicleRepository vehicles, ReleaseService releases) {
        this.rollouts = rollouts;
        this.targets = targets;
        this.vehicles = vehicles;
        this.releases = releases;
    }

    @Transactional
    public Rollout create(CreateRolloutRequest request) {
        SoftwareRelease release = releases.get(request.releaseId());

        OffsetDateTime now = OffsetDateTime.now();
        Rollout rollout = new Rollout(
                UUID.randomUUID(),
                release.getId(),
                RolloutStatus.RUNNING,
                request.percentage(),
                now,
                now);
        rollouts.save(rollout);

        assignTargets(rollout, release);
        return rollout;
    }

    @Transactional
    public Rollout changePercentage(UUID rolloutId, int percentage) {
        Rollout rollout = get(rolloutId);
        if (percentage <= rollout.getPercentage()) {
            // Shrinking a rollout would mean withdrawing an update already offered,
            // so the percentage only ever goes up.
            return rollout;
        }
        rollout.setPercentage(percentage);
        rollouts.save(rollout);
        assignTargets(rollout, releases.get(rollout.getReleaseId()));
        return rollout;
    }

    @Transactional
    public Rollout pause(UUID rolloutId) {
        Rollout rollout = get(rolloutId);
        if (rollout.getStatus() == RolloutStatus.RUNNING) {
            rollout.setStatus(RolloutStatus.PAUSED);
            rollouts.save(rollout);
        }
        return rollout;
    }

    @Transactional
    public Rollout resume(UUID rolloutId) {
        Rollout rollout = get(rolloutId);
        if (rollout.getStatus() == RolloutStatus.PAUSED) {
            rollout.setStatus(RolloutStatus.RUNNING);
            rollouts.save(rollout);
        }
        return rollout;
    }

    @Transactional(readOnly = true)
    public Rollout get(UUID rolloutId) {
        return rollouts.findById(rolloutId).orElseThrow(() -> new RolloutNotFoundException(rolloutId));
    }

    @Transactional(readOnly = true)
    public long count(UUID rolloutId, TargetState state) {
        return targets.countByRolloutIdAndState(rolloutId, state);
    }

    /**
     * Picks the share of eligible vehicles the rollout percentage asks for and adds
     * the ones that are not targeted yet. Vehicles already on the release version are
     * not eligible, and a vehicle already in this rollout is never re-added.
     */
    private void assignTargets(Rollout rollout, SoftwareRelease release) {
        List<Vehicle> eligible = vehicles.findByModelAndSwVersionNotOrderByVin(
                release.getTargetModel(), release.getVersion());

        int wanted = (int) Math.ceil(eligible.size() * rollout.getPercentage() / 100.0);
        int limit = Math.min(wanted, eligible.size());

        for (Vehicle vehicle : eligible.subList(0, limit)) {
            if (!targets.existsByRolloutIdAndVehicleId(rollout.getId(), vehicle.getId())) {
                targets.save(new RolloutTarget(
                        UUID.randomUUID(),
                        rollout.getId(),
                        vehicle.getId(),
                        TargetState.PENDING,
                        OffsetDateTime.now()));
            }
        }
    }
}
