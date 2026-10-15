package com.fleetops.fleetapi.rollout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fleetops.fleetapi.release.SoftwareRelease;
import com.fleetops.fleetapi.release.SoftwareReleaseRepository;
import com.fleetops.fleetapi.rollout.dto.UpdateResultRequest;
import com.fleetops.fleetapi.vehicle.Vehicle;
import com.fleetops.fleetapi.vehicle.VehicleRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateServiceTest {

    @Mock
    private RolloutRepository rollouts;

    @Mock
    private RolloutTargetRepository targets;

    @Mock
    private VehicleRepository vehicles;

    @Mock
    private SoftwareReleaseRepository releases;

    @InjectMocks
    private UpdateService service;

    private final UUID vehicleId = UUID.randomUUID();
    private final SoftwareRelease release = new SoftwareRelease(
            UUID.randomUUID(), "2.1.0", "ID.4", "abc123", 1024, OffsetDateTime.now());

    @Test
    void handsOutTheUpdateAndMarksTheTargetInstalling() {
        RolloutTarget target = target(TargetState.PENDING);
        Rollout rollout = rollout(target.getRolloutId(), RolloutStatus.RUNNING);

        when(vehicles.existsById(vehicleId)).thenReturn(true);
        when(targets.findFirstByVehicleIdAndStateIn(eqVehicle(), anyCollection())).thenReturn(Optional.of(target));
        when(rollouts.findById(rollout.getId())).thenReturn(Optional.of(rollout));
        when(releases.findById(release.getId())).thenReturn(Optional.of(release));

        Optional<SoftwareRelease> assigned = service.claimUpdate(vehicleId);

        assertThat(assigned).contains(release);
        assertThat(target.getState()).isEqualTo(TargetState.INSTALLING);
        verify(targets).save(target);
    }

    @Test
    void pausedRolloutHandsOutNothing() {
        RolloutTarget target = target(TargetState.PENDING);
        Rollout rollout = rollout(target.getRolloutId(), RolloutStatus.PAUSED);

        when(vehicles.existsById(vehicleId)).thenReturn(true);
        when(targets.findFirstByVehicleIdAndStateIn(eqVehicle(), anyCollection())).thenReturn(Optional.of(target));
        when(rollouts.findById(rollout.getId())).thenReturn(Optional.of(rollout));

        assertThat(service.claimUpdate(vehicleId)).isEmpty();
        assertThat(target.getState()).isEqualTo(TargetState.PENDING);
        verify(targets, never()).save(any(RolloutTarget.class));
    }

    @Test
    void successMovesTheVehicleToTheNewVersion() {
        RolloutTarget target = target(TargetState.INSTALLING);
        Vehicle vehicle = new Vehicle(vehicleId, "WVWZZZ1JZXW000001", "ID.4", "1.0.0", OffsetDateTime.now());

        when(targets.findFirstByVehicleIdAndStateIn(eqVehicle(), anyCollection())).thenReturn(Optional.of(target));
        when(vehicles.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(targets.countByRolloutIdAndStateIn(eqRollout(target), anyCollection())).thenReturn(1L);

        service.reportResult(vehicleId, new UpdateResultRequest("2.1.0", true));

        assertThat(target.getState()).isEqualTo(TargetState.SUCCEEDED);
        assertThat(vehicle.getSwVersion()).isEqualTo("2.1.0");
        verify(vehicles).save(vehicle);
    }

    @Test
    void failureLeavesTheVersionAlone() {
        RolloutTarget target = target(TargetState.INSTALLING);

        when(targets.findFirstByVehicleIdAndStateIn(eqVehicle(), anyCollection())).thenReturn(Optional.of(target));
        when(targets.countByRolloutIdAndStateIn(eqRollout(target), anyCollection())).thenReturn(1L);

        service.reportResult(vehicleId, new UpdateResultRequest("2.1.0", false));

        assertThat(target.getState()).isEqualTo(TargetState.FAILED);
        verify(vehicles, never()).save(any(Vehicle.class));
    }

    @Test
    void lastResultCompletesTheRollout() {
        RolloutTarget target = target(TargetState.INSTALLING);
        Rollout rollout = new Rollout(target.getRolloutId(), release.getId(), RolloutStatus.RUNNING, 100,
                OffsetDateTime.now(), OffsetDateTime.now());

        when(targets.findFirstByVehicleIdAndStateIn(eqVehicle(), anyCollection())).thenReturn(Optional.of(target));
        when(targets.countByRolloutIdAndStateIn(eqRollout(target), anyCollection())).thenReturn(0L);
        when(rollouts.findById(rollout.getId())).thenReturn(Optional.of(rollout));

        service.reportResult(vehicleId, new UpdateResultRequest("2.1.0", false));

        assertThat(rollout.getStatus()).isEqualTo(RolloutStatus.COMPLETED);
        verify(rollouts).save(rollout);
    }

    private UUID eqVehicle() {
        return org.mockito.ArgumentMatchers.eq(vehicleId);
    }

    private UUID eqRollout(RolloutTarget target) {
        return org.mockito.ArgumentMatchers.eq(target.getRolloutId());
    }

    private RolloutTarget target(TargetState state) {
        return new RolloutTarget(UUID.randomUUID(), UUID.randomUUID(), vehicleId, state, OffsetDateTime.now());
    }

    private Rollout rollout(UUID id, RolloutStatus status) {
        return new Rollout(id, release.getId(), status, 10,
                OffsetDateTime.now(), OffsetDateTime.now());
    }
}
