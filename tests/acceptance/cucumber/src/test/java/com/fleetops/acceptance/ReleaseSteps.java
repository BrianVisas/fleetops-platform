package com.fleetops.acceptance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.matchesPattern;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;

/** Steps for features/releases.feature (customer issue #3). */
public class ReleaseSteps {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final World world;
    private final FleetApi api;

    public ReleaseSteps(World world, FleetApi api) {
        this.world = world;
        this.api = api;
    }

    /** Publishes a release and, when it is created, remembers it in {@code into}. */
    static Response publish(FleetApi api, World world, Map<String, Object> into, String model, long sizeBytes) {
        Response response = api.publishRelease(World.randomVersion(), model, World.randomChecksum(), sizeBytes);
        world.response = response;
        if (response.statusCode() == 201) {
            into.put("id", response.path("id"));
            into.put("version", response.path("version"));
            into.put("checksum", response.path("checksum"));
            into.put("sizeBytes", response.jsonPath().getLong("sizeBytes"));
        }
        return response;
    }

    // ---- Given ----

    @Given("a published release")
    public void aPublishedRelease() {
        publish(api, world, world.release, World.randomModel(), 1024).then().statusCode(201);
    }

    @Given("a second release published after it")
    public void aSecondRelease() {
        publish(api, world, world.secondRelease, World.randomModel(), 1024).then().statusCode(201);
    }

    // ---- When ----

    @When("I publish a release of a new version for model {string}")
    public void publishForModel(String model) {
        world.requestedModel = model;
        publish(api, world, world.release, model, 1024);
    }

    @When("I publish the same version again")
    public void publishAgain() {
        world.response = api.publishRelease((String) world.release.get("version"), World.randomModel(),
                World.randomChecksum(), 1024);
    }

    @When("I publish a release with a size of {int} bytes")
    public void publishWithSize(int size) {
        publish(api, world, world.release, World.randomModel(), size);
    }

    @When("I list the releases")
    public void listReleases() {
        world.response = api.listReleases();
    }

    // ---- Then ----

    @Then("the response contains the release with an id")
    public void responseHasRelease() {
        String id = world.response.path("id");
        assertThat(id, matchesPattern(UUID_PATTERN));
        assertThat(world.response.path("targetModel"), equalTo(world.requestedModel));
        assertThat(world.response.header("Location"), endsWith("/api/v1/releases/" + id));
    }

    @Then("the second release is listed before the first")
    public void secondBeforeFirst() {
        List<String> ids = world.response.jsonPath().getList("id", String.class);
        int second = ids.indexOf((String) world.secondRelease.get("id"));
        int first = ids.indexOf((String) world.release.get("id"));
        assertThat(second, greaterThanOrEqualTo(0));
        assertThat(second, lessThan(first));
    }
}
