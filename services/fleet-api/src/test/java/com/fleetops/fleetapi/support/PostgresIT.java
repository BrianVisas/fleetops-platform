package com.fleetops.fleetapi.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for integration tests. Starts one Postgres container for the whole
 * test run and points the application's datasource at it, so Flyway applies the
 * real migrations to a real database.
 *
 * The container is a static singleton started in an initialiser rather than a
 * JUnit-managed @Container: a managed container is started and stopped per test
 * class, which costs several seconds each time. Testcontainers' Ryuk sidecar
 * removes this one when the JVM exits - and this way no JUnit extension, and so
 * no testcontainers-junit-jupiter dependency, is needed at all.
 *
 * Testcontainers 2.x: the class lives in org.testcontainers.postgresql and is no
 * longer generic, so there is no <?> on the type.
 */
@SpringBootTest
public abstract class PostgresIT {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }

    @Autowired
    private JdbcTemplate resetTemplate;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /**
     * One database serves the whole run, so rows a test writes are still there
     * when the next class starts. JUnit does not promise an execution order, so
     * "the database is empty" is only true if something makes it true - here,
     * before every test in every subclass.
     *
     * This was not theoretical: SchemaMigrationIT inserts a rollout_target row
     * and leaves it, and when it happened to run before VehicleRepositoryIT, a
     * plain deleteAll() on vehicle failed against rollout_target_vehicle_id_fkey.
     * The same test suite passed locally and failed in CI purely on class order.
     *
     * The table list comes from the catalogue rather than being hard-coded, so a
     * table added in a future migration is covered without anyone remembering to
     * update this. CASCADE deals with the foreign keys in one statement, which is
     * also why the order the tables are listed in does not matter.
     *
     * flyway_schema_history is excluded deliberately: truncating it would make
     * Flyway believe the schema is unmigrated, and SchemaMigrationIT asserts on
     * its contents.
     */
    @BeforeEach
    void resetDatabase() {
        String tables = resetTemplate.queryForObject(
                """
                select string_agg(format('%I', tablename), ', ')
                from pg_tables
                where schemaname = 'public'
                  and tablename <> 'flyway_schema_history'
                """,
                String.class);

        if (tables != null) {
            resetTemplate.execute("truncate table " + tables + " cascade");
        }
    }
}
