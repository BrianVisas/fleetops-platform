package com.fleetops.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fleetops.e2e.pages.ReleasesPage;
import com.fleetops.e2e.pages.RolloutPage;
import com.fleetops.e2e.pages.VehiclesPage;
import com.fleetops.e2e.support.BrowserTest;
import com.fleetops.e2e.support.FleetApi;
import com.microsoft.playwright.APIResponse;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Issue #40: see the fleet and steer rollouts in a browser. One test per
 * acceptance criterion, with the same names in the TypeScript and Python suites.
 */
@Tag("issue-40")
class DashboardTest extends BrowserTest {

    @Test
    @DisplayName("filtering by model shows only that model, 20 per page")
    void filteringByModel() {
        String model = FleetApi.randomModel();
        api.registerVehicles(model, 25);

        VehiclesPage vehicles = new VehiclesPage(page);
        vehicles.open();
        vehicles.filterByModel(model);

        assertThat(vehicles.modelCells).hasText(times(20, model));
        assertThat(vehicles.pageInfo).hasText("Page 1 of 2");
        assertThat(vehicles.previousButton).isDisabled();

        vehicles.nextButton.click();
        assertThat(vehicles.modelCells).hasText(times(5, model));
        assertThat(vehicles.pageInfo).hasText("Page 2 of 2");
        assertThat(vehicles.nextButton).isDisabled();

        vehicles.previousButton.click();
        assertThat(vehicles.modelCells).hasText(times(20, model));
        assertThat(vehicles.pageInfo).hasText("Page 1 of 2");
    }

    @Test
    @DisplayName("starting a rollout shows it with its status and counts")
    void startingARollout() {
        String model = FleetApi.randomModel();
        api.registerVehicles(model, 4);
        FleetApi.Release release = api.publishRelease(model);

        ReleasesPage releases = new ReleasesPage(page);
        releases.open();
        releases.startRollout(release.version() + " (" + model + ")", 50);

        RolloutPage rollout = new RolloutPage(page);
        assertThat(page).hasURL(Pattern.compile("#/rollouts/[0-9a-f-]{36}$"));
        assertThat(rollout.status).hasText("RUNNING");
        assertThat(rollout.percentage).hasText("50%");
        // 50% of 4 vehicles
        assertThat(rollout.pending).hasText("2");
        assertThat(rollout.installing).hasText("0");
        assertThat(rollout.succeeded).hasText("0");
        assertThat(rollout.failed).hasText("0");
    }

    @Test
    @DisplayName("pausing and resuming a rollout")
    void pausingAndResuming() {
        String model = FleetApi.randomModel();
        api.registerVehicles(model, 2);
        FleetApi.Release release = api.publishRelease(model);
        String rolloutId = api.startRollout(release.id(), 100);

        RolloutPage rollout = new RolloutPage(page);
        rollout.open(rolloutId);
        assertThat(rollout.status).hasText("RUNNING");

        rollout.pauseButton.click();
        assertThat(rollout.status).hasText("PAUSED");
        assertThat(rollout.pauseReason).hasText("MANUAL");
        assertThat(rollout.pauseButton).isDisabled();

        rollout.resumeButton.click();
        assertThat(rollout.status).hasText("RUNNING");
        assertThat(rollout.pauseReason).hasText("none");
        assertThat(rollout.resumeButton).isDisabled();
    }

    @Test
    @DisplayName("a rollout that paused itself shows the reason FAILURE_THRESHOLD")
    void autoPausedRollout() {
        String model = FleetApi.randomModel();
        List<String> vehicles = api.registerVehicles(model, 10);
        FleetApi.Release release = api.publishRelease(model);
        String rolloutId = api.startRollout(release.id(), 100);
        // 2 of 5 finished installations failed: 40%, over the 20% threshold (#6).
        for (int i = 0; i < 5; i++) {
            api.install(vehicles.get(i), release.version(), i < 3);
        }

        RolloutPage rollout = new RolloutPage(page);
        rollout.open(rolloutId);

        assertThat(rollout.status).hasText("PAUSED");
        assertThat(rollout.pauseReason).hasText("FAILURE_THRESHOLD");
        assertThat(rollout.pending).hasText("5");
        assertThat(rollout.succeeded).hasText("3");
        assertThat(rollout.failed).hasText("2");
    }

    @Test
    @DisplayName("an API error is shown to the operator")
    void apiError() {
        String missing = UUID.randomUUID().toString();
        APIResponse response = api.get("/api/v1/rollouts/" + missing);
        assertEquals(404, response.status(), response.text());
        String message = FleetApi.json(response).get("message").getAsString();
        // fleet-api's own message, not the dashboard's status-line fallback
        assertTrue(message.contains(missing), message);

        RolloutPage rollout = new RolloutPage(page);
        rollout.open(missing);

        assertThat(rollout.error).hasText(message);
    }

    private static String[] times(int count, String text) {
        return Collections.nCopies(count, text).toArray(String[]::new);
    }
}
