package com.fleetops.fleetapi.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fleetops.fleetapi.common.DuplicateVinException;
import com.fleetops.fleetapi.common.VehicleNotFoundException;
import com.fleetops.fleetapi.vehicle.dto.CreateVehicleRequest;
import com.fleetops.fleetapi.vehicle.dto.UpdateVehicleRequest;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository repository;

    @InjectMocks
    private VehicleService service;

    private static final String VIN = "WVWZZZ1JZXW000001";

    @Test
    void registersVehicleWithGeneratedIdAndTimestamp() {
        when(repository.existsByVin(VIN)).thenReturn(false);
        when(repository.saveAndFlush(any(Vehicle.class))).thenAnswer(call -> call.getArgument(0));

        Vehicle saved = service.register(new CreateVehicleRequest(VIN, "ID.4", "1.0.0"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVin()).isEqualTo(VIN);
        assertThat(saved.getModel()).isEqualTo("ID.4");
        assertThat(saved.getSwVersion()).isEqualTo("1.0.0");
        assertThat(saved.getRegisteredAt()).isNotNull();
    }

    @Test
    void rejectsDuplicateVinWithoutSaving() {
        when(repository.existsByVin(VIN)).thenReturn(true);

        assertThatThrownBy(() -> service.register(new CreateVehicleRequest(VIN, "ID.4", "1.0.0")))
                .isInstanceOf(DuplicateVinException.class)
                .hasMessageContaining(VIN);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void getThrowsWhenVehicleIsMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(VehicleNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void updateChangesModelAndVersionButKeepsVinAndId() {
        UUID id = UUID.randomUUID();
        Vehicle existing = new Vehicle(id, VIN, "ID.4", "1.0.0", OffsetDateTime.now());
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(Vehicle.class))).thenAnswer(call -> call.getArgument(0));

        Vehicle updated = service.update(id, new UpdateVehicleRequest("ID.5", "2.0.0"));

        assertThat(updated.getId()).isEqualTo(id);
        assertThat(updated.getVin()).isEqualTo(VIN);
        assertThat(updated.getModel()).isEqualTo("ID.5");
        assertThat(updated.getSwVersion()).isEqualTo("2.0.0");
    }

    @Test
    void deleteThrowsWhenVehicleIsMissing() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(VehicleNotFoundException.class);

        verify(repository, never()).deleteById(id);
    }
}
