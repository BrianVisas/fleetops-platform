package com.fleetops.acceptance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import java.util.Map;
import java.util.UUID;

/** Steps for features/telemetry.feature (customer issue #2). */
public class TelemetrySteps {

    private final World world;
    private final TelemetryApi telemetry;

    public TelemetrySteps(World world, TelemetryApi telemetry) {
        this.world = world;
        this.telemetry = telemetry;
    }

    private static double speedOf(Response response) {
        return response.jsonPath().getDouble("speedKmh");
    }

    // ---- Given ----

    @Given("the vehicle sent a reading {int} minutes ago with speed {double} km\\/h")
    public void vehicleSentReading(int minutes, double speed) {
        telemetry.send(TelemetryApi.reading(world.vehicle.get("id"), speed, minutes)).then().statusCode(202);
    }

    // ---- When ----

    @When("the vehicle sends a reading with speed {double} km\\/h")
    public void vehicleSendsReading(double speed) {
        world.response = telemetry.send(TelemetryApi.reading(world.vehicle.get("id"), speed, 0));
    }

    @When("a vehicle that is not registered sends a reading")
    public void unknownVehicleSendsReading() {
        world.unknownVehicleId = UUID.randomUUID().toString();
        world.response = telemetry.send(TelemetryApi.reading(world.unknownVehicleId, 50.0, 0));
    }

    @When("the vehicle sends a reading where {word} is {word}")
    public void vehicleSendsBadReading(String field, String problem) {
        Map<String, Object> reading = TelemetryApi.reading(world.vehicle.get("id"), 50.0, 0);
        switch (problem) {
            case "missing" -> reading.remove(field);
            case "non-numeric" -> reading.put(field, "not-a-number");
            default -> throw new IllegalArgumentException("unknown problem: " + problem);
        }
        world.response = telemetry.send(reading);
    }

    @When("I ask for the vehicle's latest reading")
    public void askLatest() {
        world.response = telemetry.latest(world.vehicle.get("id"));
    }

    // ---- Then ----

    @Then("the vehicle's latest reading shows speed {double} km\\/h")
    public void latestShowsSpeed(double speed) {
        Response response = telemetry.latest(world.vehicle.get("id"));
        response.then().statusCode(200);
        assertThat(speedOf(response), closeTo(speed, 0.001));
    }

    @Then("that vehicle has no latest reading")
    public void unknownHasNoReading() {
        telemetry.latest(world.unknownVehicleId).then().statusCode(404);
    }

    @Then("the reading shows speed {double} km\\/h")
    public void readingShowsSpeed(double speed) {
        assertThat(speedOf(world.response), closeTo(speed, 0.001));
    }
}
