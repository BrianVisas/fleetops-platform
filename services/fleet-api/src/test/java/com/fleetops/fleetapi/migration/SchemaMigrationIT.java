package com.fleetops.fleetapi.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fleetops.fleetapi.support.PostgresIT;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the migrations apply cleanly to an empty database and that the
 * constraints they declare actually hold. Without this, a broken migration is
 * only discovered on the next deployment.
 */
class SchemaMigrationIT extends PostgresIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyTableExistsAfterMigration() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'",
                String.class);

        assertThat(tables).contains(
                "vehicle", "software_release", "rollout", "rollout_target", "telemetry",
                "flyway_schema_history");
    }

    @Test
    void everyMigrationIsRecordedAsApplied() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success = true order by installed_rank",
                String.class);

        assertThat(versions).containsExactly("1", "2", "3");
    }

    @Test
    void rolloutTargetKeepsTheRolloutVehiclePairUnique() {
        UUID releaseId = UUID.randomUUID();
        UUID rolloutId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();

        jdbc.update("insert into vehicle (id, vin, model, sw_version) values (?, ?, ?, ?)",
                vehicleId, "WVWZZZ1JZXW000900", "ID.4", "1.0.0");
        jdbc.update("""
                insert into software_release (id, version, target_model, checksum, size_bytes)
                values (?, ?, ?, ?, ?)""",
                releaseId, "9.9.9", "ID.4", "abc", 1024);
        jdbc.update("insert into rollout (id, release_id, status, percentage) values (?, ?, ?, ?)",
                rolloutId, releaseId, "RUNNING", 100);
        jdbc.update("insert into rollout_target (id, rollout_id, vehicle_id, state) values (?, ?, ?, ?)",
                UUID.randomUUID(), rolloutId, vehicleId, "PENDING");

        assertThatThrownBy(() -> jdbc.update(
                "insert into rollout_target (id, rollout_id, vehicle_id, state) values (?, ?, ?, ?)",
                UUID.randomUUID(), rolloutId, vehicleId, "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsAnUnknownRolloutStatus() {
        UUID releaseId = UUID.randomUUID();
        jdbc.update("""
                insert into software_release (id, version, target_model, checksum, size_bytes)
                values (?, ?, ?, ?, ?)""",
                releaseId, "9.9.8", "ID.4", "abc", 1024);

        assertThatThrownBy(() -> jdbc.update(
                "insert into rollout (id, release_id, status, percentage) values (?, ?, ?, ?)",
                UUID.randomUUID(), releaseId, "DEFINITELY_NOT_A_STATUS", 50))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsAnOutOfRangePercentage() {
        UUID releaseId = UUID.randomUUID();
        jdbc.update("""
                insert into software_release (id, version, target_model, checksum, size_bytes)
                values (?, ?, ?, ?, ?)""",
                releaseId, "9.9.7", "ID.4", "abc", 1024);

        assertThatThrownBy(() -> jdbc.update(
                "insert into rollout (id, release_id, status, percentage) values (?, ?, ?, ?)",
                UUID.randomUUID(), releaseId, "RUNNING", 150))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aPausedRolloutMustSayWhy() {
        UUID releaseId = release("9.9.6");

        assertThatThrownBy(() -> jdbc.update(
                "insert into rollout (id, release_id, status, percentage) values (?, ?, ?, ?)",
                UUID.randomUUID(), releaseId, "PAUSED", 50))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyAPausedRolloutHasAPauseReason() {
        UUID releaseId = release("9.9.5");

        assertThatThrownBy(() -> jdbc.update(
                "insert into rollout (id, release_id, status, pause_reason, percentage) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(), releaseId, "RUNNING", "MANUAL", 50))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsAnUnknownPauseReason() {
        UUID releaseId = release("9.9.4");

        assertThatThrownBy(() -> jdbc.update(
                "insert into rollout (id, release_id, status, pause_reason, percentage) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(), releaseId, "PAUSED", "BORED", 50))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aPausedRolloutWithAKnownReasonIsAccepted() {
        UUID releaseId = release("9.9.3");

        jdbc.update(
                "insert into rollout (id, release_id, status, pause_reason, percentage) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(), releaseId, "PAUSED", "FAILURE_THRESHOLD", 50);

        assertThat(jdbc.queryForObject("select count(*) from rollout where pause_reason = 'FAILURE_THRESHOLD'",
                Integer.class)).isEqualTo(1);
    }

    private UUID release(String version) {
        UUID releaseId = UUID.randomUUID();
        jdbc.update("""
                insert into software_release (id, version, target_model, checksum, size_bytes)
                values (?, ?, ?, ?, ?)""",
                releaseId, version, "ID.4", "abc", 1024);
        return releaseId;
    }
}
