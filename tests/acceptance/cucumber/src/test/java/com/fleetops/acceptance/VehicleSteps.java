package com.fleetops.acceptance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import java.util.List;
import java.util.UUID;

/** Steps for features/vehicles.feature (customer issue #1). */
public class VehicleSteps {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final World world;
    private final FleetApi api;

    public VehicleSteps(World world, FleetApi api) {
        this.world = world;
        this.api = api;
    }

    private Response register(String vin, String model, String swVersion) {
        world.vehicle.put("vin", vin);
        world.vehicle.put("model", model);
        world.vehicle.put("swVersion", swVersion);
        world.response = api.register(vin, model, swVersion);
        return world.response;
    }

    // ---- Given ----

    @Given("a registered vehicle")
    public void aRegisteredVehicle() {
        Response response = register(World.randomVin(), "ID.4", "1.0.0");
        response.then().statusCode(201);
        world.vehicle.put("id", response.path("id"));
    }

    @Given("{int} registered vehicles of one model and {int} of another")
    public void vehiclesOfTwoModels(int first, int second) {
        world.firstModel = World.randomModel();
        String otherModel = World.randomModel();
        for (int i = 0; i < first; i++) {
            api.register(World.randomVin(), world.firstModel, "1.0.0").then().statusCode(201);
        }
        for (int i = 0; i < second; i++) {
            api.register(World.randomVin(), otherModel, "1.0.0").then().statusCode(201);
        }
    }

    // ---- When ----

    @When("I register a vehicle of model {string} with software version {string}")
    public void registerVehicle(String model, String swVersion) {
        register(World.randomVin(), model, swVersion);
    }

    @When("I register another vehicle with the same VIN")
    public void registerSameVin() {
        world.response = api.register(world.vehicle.get("vin"), "ID.3", "1.0.0");
    }

    @When("I register a vehicle with the VIN {string}")
    public void registerWithVin(String vin) {
        register(vin, "ID.4", "1.0.0");
    }

    @When("I fetch that vehicle")
    public void fetchVehicle() {
        world.response = api.get(world.vehicle.get("id"));
    }

    @When("I update its software version to {string}")
    public void updateVehicle(String swVersion) {
        world.response = api.update(world.vehicle.get("id"), world.vehicle.get("model"), swVersion);
    }

    @When("I delete that vehicle")
    public void deleteVehicle() {
        world.response = api.delete(world.vehicle.get("id"));
    }

    @When("I {word} a vehicle that does not exist")
    public void operateOnMissingVehicle(String operation) {
        String missing = UUID.randomUUID().toString();
        world.response = switch (operation) {
            case "fetch" -> api.get(missing);
            case "update" -> api.update(missing, "ID.4", "1.0.0");
            case "delete" -> api.delete(missing);
            default -> throw new IllegalArgumentException("unknown operation: " + operation);
        };
    }

    @When("I list vehicles of the first model, {int} per page")
    public void listFirstModel(int size) {
        world.pageSize = size;
    }

    // ---- Then ----

    @Then("the response status is {int}")
    public void responseStatus(int status) {
        world.response.then().statusCode(status);
    }

    @Then("the response contains the new vehicle with an id")
    public void responseHasNewVehicle() {
        String id = world.response.path("id");
        assertThat(id, matchesPattern(UUID_PATTERN));
        assertThat(world.response.path("vin"), equalTo(world.vehicle.get("vin")));
        assertThat(world.response.header("Location"), endsWith("/api/v1/vehicles/" + id));
    }

    @Then("the error message mentions {string}")
    public void errorMentions(String text) {
        assertThat(world.response.path("message"), containsString(text));
    }

    @Then("the response shows the vehicle's VIN, model and software version")
    public void responseShowsVehicle() {
        assertThat(world.response.path("vin"), equalTo(world.vehicle.get("vin")));
        assertThat(world.response.path("model"), equalTo(world.vehicle.get("model")));
        assertThat(world.response.path("swVersion"), equalTo(world.vehicle.get("swVersion")));
    }

    @Then("fetching the vehicle shows software version {string}")
    public void fetchedVersion(String swVersion) {
        Response response = api.get(world.vehicle.get("id"));
        response.then().statusCode(200);
        assertThat(response.path("swVersion"), equalTo(swVersion));
    }

    @Then("fetching the vehicle returns status {int}")
    public void fetchedStatus(int status) {
        api.get(world.vehicle.get("id")).then().statusCode(status);
    }

    @Then("page {int} holds {int} vehicle(s), all of the first model")
    public void pageHolds(int page, int count) {
        Response response = api.list(world.firstModel, page - 1, world.pageSize);
        response.then().statusCode(200);
        List<String> models = response.jsonPath().getList("content.model", String.class);
        assertThat(models, hasSize(count));
        assertThat(models, everyItem(equalTo(world.firstModel)));
    }
}
