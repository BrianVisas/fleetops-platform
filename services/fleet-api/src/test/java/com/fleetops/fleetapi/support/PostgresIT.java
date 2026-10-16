package com.fleetops.fleetapi.support;

import org.springframework.boot.test.context.SpringBootTest;
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

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
