package com.fleetops.acceptance;

import io.cucumber.java.After;
import io.cucumber.java.Scenario;

/** Attaches the scenario's HTTP exchanges to the report when it fails. */
public class Hooks {

    private final HttpLog log;

    public Hooks(HttpLog log) {
        this.log = log;
    }

    @After
    public void attachHttpLogOnFailure(Scenario scenario) {
        if (scenario.isFailed()) {
            scenario.attach(log.text(), "text/plain", "HTTP requests and responses");
        }
    }
}
