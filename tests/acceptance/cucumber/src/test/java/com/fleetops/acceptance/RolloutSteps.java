package com.fleetops.acceptance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.oneOf;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;

/**
 * Steps shared by features/rollouts.feature (#4), updates.feature (#5) and auto_pause.feature (#6).
 * Vehicles are numbered from 1 in the order the scenario registered them.
 */
public class RolloutSteps {

    private final World world;
    private final FleetApi api;

    public RolloutSteps(World world, FleetApi api) {
        this.world = world;
        this.api = api;
    }

    private String vehicle(int number) {
        return world.vehicles.get(number - 1);
    }

    private String releaseVersion() {
        return (String) world.release.get("version");
    }

    private Response rollout() {
        Response response = api.getRollout(world.rolloutId);
        response.then().statusCode(200);
        return response;
    }

    private static long count(Response rollout, String state) {
        return rollout.jsonPath().getLong(state);
    }

    // ---- Given ----

    @Given("{int} vehicle(s) of a new model on software version {string}")
    public void vehiclesOfNewModel(int count, String swVersion) {
        world.model = World.randomModel();
        for (int i = 0; i < count; i++) {
            Response response = api.register(World.randomVin(), world.model, swVersion);
            response.then().statusCode(201);
            world.vehicles.add(response.path("id"));
        }
    }

    @Given("a release of a new version for that model")
    public void releaseForModel() {
        ReleaseSteps.publish(api, world, world.release, world.model, 1024).then().statusCode(201);
    }

    @Given("{int} vehicle(s) of that model already on the release version")
    public void vehiclesOnReleaseVersion(int count) {
        for (int i = 0; i < count; i++) {
            api.register(World.randomVin(), world.model, releaseVersion()).then().statusCode(201);
        }
    }

    @Given("{int} vehicle(s) of another model")
    public void vehiclesOfAnotherModel(int count) {
        String model = World.randomModel();
        for (int i = 0; i < count; i++) {
            api.register(World.randomVin(), model, "1.0.0").then().statusCode(201);
        }
    }

    @Given("a rollout of the release to {int}%")
    public void rolloutExists(int percentage) {
        Response response = api.createRollout((String) world.release.get("id"), percentage);
        response.then().statusCode(201);
        world.rolloutId = response.path("id");
    }

    @Given("the rollout is paused")
    public void rolloutIsPaused() {
        api.pause(world.rolloutId).then().statusCode(200);
    }

    @Given("every vehicle of that model has asked for its update")
    public void everyVehicleAsked() {
        for (String id : world.vehicles) {
            assertThat(api.claimUpdate(id).statusCode(), oneOf(200, 204));
        }
    }

    @Given("vehicle {int} has asked for its update")
    public void vehicleHasAsked(int number) {
        api.claimUpdate(vehicle(number)).then().statusCode(200);
    }

    @Given("vehicle {int} has installed the update successfully")
    public void vehicleInstalled(int number) {
        vehicleHasAsked(number);
        api.reportResult(vehicle(number), releaseVersion(), true).then().statusCode(202);
    }

    @Given("vehicle {int} has failed to install the update")
    public void vehicleFailed(int number) {
        vehicleHasAsked(number);
        api.reportResult(vehicle(number), releaseVersion(), false).then().statusCode(202);
    }

    @Given("vehicles {int} to {int} have installed the update successfully")
    public void vehiclesInstalled(int first, int last) {
        for (int number = first; number <= last; number++) {
            vehicleInstalled(number);
        }
    }

    @Given("vehicles {int} to {int} have failed to install the update")
    public void vehiclesFailed(int first, int last) {
        for (int number = first; number <= last; number++) {
            vehicleFailed(number);
        }
    }

    // ---- When ----

    @When("I create a rollout of the release to {int}%")
    public void createRollout(int percentage) {
        world.response = api.createRollout((String) world.release.get("id"), percentage);
        if (world.response.statusCode() == 201) {
            world.rolloutId = world.response.path("id");
        }
    }

    @When("I raise the rollout to {int}%")
    public void raiseRollout(int percentage) {
        world.response = api.changePercentage(world.rolloutId, percentage);
    }

    @When("I pause the rollout")
    public void pauseRollout() {
        world.response = api.pause(world.rolloutId);
    }

    @When("I look at the rollout")
    public void lookAtRollout() {
        world.response = api.getRollout(world.rolloutId);
    }

    @When("vehicle {int} asks for its update")
    public void vehicleAsks(int number) {
        world.response = api.claimUpdate(vehicle(number));
    }

    @When("vehicle {int} reports that the installation succeeded")
    public void vehicleReportsSuccess(int number) {
        world.response = api.reportResult(vehicle(number), releaseVersion(), true);
    }

    @When("vehicle {int} reports that the installation failed")
    public void vehicleReportsFailure(int number) {
        world.response = api.reportResult(vehicle(number), releaseVersion(), false);
    }

    // ---- Then ----

    @Then("the rollout targets {int} vehicle(s)")
    public void rolloutTargets(int count) {
        Response rollout = rollout();
        long total = count(rollout, "pending") + count(rollout, "installing")
                + count(rollout, "succeeded") + count(rollout, "failed");
        assertThat(total, equalTo((long) count));
    }

    @Then("the rollout shows {int} pending, {int} installing, {int} succeeded and {int} failed")
    public void rolloutShows(int pending, int installing, int succeeded, int failed) {
        Response rollout = rollout();
        assertThat(java.util.List.of(count(rollout, "pending"), count(rollout, "installing"),
                        count(rollout, "succeeded"), count(rollout, "failed")),
                contains((long) pending, (long) installing, (long) succeeded, (long) failed));
    }

    @Then("the rollout status is {word}")
    public void rolloutStatus(String status) {
        assertThat(rollout().path("status"), equalTo(status));
    }

    @Then("the response offers the release")
    public void responseOffersRelease() {
        assertThat(world.response.path("version"), equalTo(world.release.get("version")));
        assertThat(world.response.path("checksum"), equalTo(world.release.get("checksum")));
        assertThat(world.response.jsonPath().getLong("sizeBytes"), equalTo(world.release.get("sizeBytes")));
    }

    @Then("vehicle {int} is on the release version")
    public void vehicleOnReleaseVersion(int number) {
        assertThat(api.get(vehicle(number)).path("swVersion"), equalTo(releaseVersion()));
    }

    @Then("vehicle {int} is still on software version {string}")
    public void vehicleStillOn(int number, String swVersion) {
        assertThat(api.get(vehicle(number)).path("swVersion"), equalTo(swVersion));
    }

    @Then("the rollout's pause reason is {word}")
    public void rolloutPauseReason(String reason) {
        assertThat(rollout().path("pauseReason"), equalTo(reason));
    }
}
