package com.fleetops.fleetapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.fleetops.fleetapi.support.PostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * The context test the generator produced, now pointed at a throwaway container
 * instead of whatever Postgres happens to run on the developer's laptop.
 */
class FleetApiApplicationIT extends PostgresIT {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
        assertThat(context.containsBean("vehicleService")).isTrue();
        assertThat(context.containsBean("rolloutService")).isTrue();
    }
}
