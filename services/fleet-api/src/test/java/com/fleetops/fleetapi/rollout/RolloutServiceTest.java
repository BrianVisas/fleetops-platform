package com.fleetops.fleetapi.rollout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fleetops.fleetapi.release.ReleaseService;
import com.fleetops.fleetapi.release.SoftwareRelease;
import com.fleetops.fleetapi.rollout.dto.CreateRolloutRequest;
import com.fleetops.fleetapi.vehicle.Vehicle;
import com.fleetops.fleetapi.vehicle.VehicleRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RolloutServiceTest {

    @Mock
    private RolloutRepository rollouts;

    @Mock
    private RolloutTargetRepository targets;

    @Mock
    private VehicleRepository vehicles;

    @Mock
    private ReleaseService releases;

    @InjectMocks
    private RolloutService service;

    private final SoftwareRelease release = new SoftwareRelease(
            UUID.randomUUID(), "2.1.0", "ID.4", "abc123", 1024, OffsetDateTime.now());

    private List<Vehicle> fleetOf(int count) {
        List<Vehicle> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new Vehicle(UUID.randomUUID(), String.format("WVWZZZ1JZXW%06d", i),
                    "ID.4", "1.0.0", OffsetDateTime.now()));
        }
        return list;
    }

    @Test
    void tenPercentOfTenVehiclesTargetsOne() {
        when(releases.get(release.getId())).thenReturn(release);
        when(vehicles.findByModelAndSwVersionNotOrderByVin("ID.4", "2.1.0")).thenReturn(fleetOf(10));
        when(targets.existsByRolloutIdAndVehicleId(any(), any())).thenReturn(false);

        service.create(new CreateRolloutRequest(release.getId(), 10));

        verify(targets, org.mockito.Mockito.times(1)).save(any(RolloutTarget.class));
    }

    @Test
    void percentageRoundsUp() {
        when(releases.get(release.getId())).thenReturn(release);
        when(vehicles.findByModelAndSwVersionNotOrderByVin("ID.4", "2.1.0")).thenReturn(fleetOf(7));
        when(targets.existsByRolloutIdAndVehicleId(any(), any())).thenReturn(false);

        service.create(new CreateRolloutRequest(release.getId(), 10));

        // ceil(7 * 0.1) = 1, never 0 - a rollout should always reach at least one vehicle
        verify(targets, org.mockito.Mockito.times(1)).save(any(RolloutTarget.class));
    }

    @Test
    void newTargetsStartPendingAndBelongToTheRollout() {
        when(releases.get(release.getId())).thenReturn(release);
        when(vehicles.findByModelAndSwVersionNotOrderByVin("ID.4", "2.1.0")).thenReturn(fleetOf(2));
        when(targets.existsByRolloutIdAndVehicleId(any(), any())).thenReturn(false);

        Rollout rollout = service.create(new CreateRolloutRequest(release.getId(), 100));

        ArgumentCaptor<RolloutTarget> saved = ArgumentCaptor.forClass(RolloutTarget.class);
        verify(targets, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .allSatisfy(target -> {
                    assertThat(target.getState()).isEqualTo(TargetState.PENDING);
                    assertThat(target.getRolloutId()).isEqualTo(rollout.getId());
                });
        assertThat(rollout.getStatus()).isEqualTo(RolloutStatus.RUNNING);
    }

    @Test
    void vehiclesAlreadyInTheRolloutAreNotAddedTwice() {
        when(releases.get(release.getId())).thenReturn(release);
        when(vehicles.findByModelAndSwVersionNotOrderByVin("ID.4", "2.1.0")).thenReturn(fleetOf(4));
        when(targets.existsByRolloutIdAndVehicleId(any(), any())).thenReturn(true);

        service.create(new CreateRolloutRequest(release.getId(), 100));

        verify(targets, never()).save(any(RolloutTarget.class));
    }

    @Test
    void pauseSetsStatusToPaused() {
        Rollout rollout = runningRollout();
        when(rollouts.findById(rollout.getId())).thenReturn(Optional.of(rollout));

        Rollout paused = service.pause(rollout.getId());

        assertThat(paused.getStatus()).isEqualTo(RolloutStatus.PAUSED);
        verify(rollouts).save(rollout);
    }

    @Test
    void percentageIsNeverLowered() {
        Rollout rollout = runningRollout();
        when(rollouts.findById(rollout.getId())).thenReturn(Optional.of(rollout));

        Rollout unchanged = service.changePercentage(rollout.getId(), 10);

        assertThat(unchanged.getPercentage()).isEqualTo(50);
        verify(rollouts, never()).save(any(Rollout.class));
        verify(releases, never()).get(eq(release.getId()));
    }

    private Rollout runningRollout() {
        OffsetDateTime now = OffsetDateTime.now();
        return new Rollout(UUID.randomUUID(), release.getId(), RolloutStatus.RUNNING, 50, now, now);
    }
}
