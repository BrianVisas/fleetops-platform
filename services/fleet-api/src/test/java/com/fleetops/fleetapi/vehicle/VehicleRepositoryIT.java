package com.fleetops.fleetapi.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fleetops.fleetapi.support.PostgresIT;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * What mocks cannot prove: that the entity mapping, the derived queries and the
 * schema Flyway created actually agree with each other.
 */
class VehicleRepositoryIT extends PostgresIT {

    @Autowired
    private VehicleRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private Vehicle vehicle(String vin, String model, String swVersion) {
        return new Vehicle(UUID.randomUUID(), vin, model, swVersion, OffsetDateTime.now());
    }

    @Test
    void theDatabaseRejectsADuplicateVin() {
        repository.saveAndFlush(vehicle("WVWZZZ1JZXW000001", "ID.4", "1.0.0"));

        assertThatThrownBy(() -> repository.saveAndFlush(vehicle("WVWZZZ1JZXW000001", "ID.5", "1.0.0")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByVinSeesAStoredVehicle() {
        repository.saveAndFlush(vehicle("WVWZZZ1JZXW000002", "ID.4", "1.0.0"));

        assertThat(repository.existsByVin("WVWZZZ1JZXW000002")).isTrue();
        assertThat(repository.existsByVin("WVWZZZ1JZXW999999")).isFalse();
    }

    @Test
    void findByModelPaginatesAndFiltersByModel() {
        repository.saveAndFlush(vehicle("WVWZZZ1JZXW000010", "ID.4", "1.0.0"));
        repository.saveAndFlush(vehicle("WVWZZZ1JZXW000011", "ID.4", "1.0.0"));
        repository.saveAndFlush(vehicle("WVWZZZ1JZXW000012", "ID.5", "1.0.0"));

        Page<Vehicle> firstPage = repository.findByModel("ID.4", PageRequest.of(0, 1));

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(1);
        assertThat(firstPage.getContent().get(0).getModel()).isEqualTo("ID.4");
    }

    @Test
    void eligibleVehiclesExcludeThoseAlreadyOnTheReleaseVersion() {
        repository.saveAndFlush(vehicle("WVWZZZ1JZXW000022", "ID.4", "1.0.0"));
        repository.saveAndFlush(vehicle("WVWZZZ1JZXW000020", "ID.4", "2.1.0"));
        repository.saveAndFlush(vehicle("WVWZZZ1JZXW000021", "ID.4", "1.0.0"));
        repository.saveAndFlush(vehicle("WVWZZZ1JZXW000023", "ID.5", "1.0.0"));

        List<Vehicle> eligible = repository.findByModelAndSwVersionNotOrderByVin("ID.4", "2.1.0");

        assertThat(eligible).hasSize(2);
        // Ordered by VIN, which is what makes a percentage pick the same vehicles twice.
        assertThat(eligible.get(0).getVin()).isEqualTo("WVWZZZ1JZXW000021");
        assertThat(eligible.get(1).getVin()).isEqualTo("WVWZZZ1JZXW000022");
    }
}
