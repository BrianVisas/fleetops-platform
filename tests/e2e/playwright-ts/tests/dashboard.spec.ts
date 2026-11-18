import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { ReleasesPage } from "../pages/releases-page";
import { RolloutPage } from "../pages/rollout-page";
import { VehiclesPage } from "../pages/vehicles-page";
import { FleetApi, randomModel } from "../support/fleet-api";

// Issue #40: see the fleet and steer rollouts in a browser. One test per
// acceptance criterion, with the same names in the Java and Python suites.
test.describe("dashboard", { tag: "@issue-40" }, () => {
  test("filtering by model shows only that model, 20 per page", async ({ page, request }) => {
    const model = randomModel();
    await new FleetApi(request).registerVehicles(model, 25);

    const vehicles = new VehiclesPage(page);
    await vehicles.open();
    await vehicles.filterByModel(model);

    await expect(vehicles.modelCells).toHaveText(Array(20).fill(model));
    await expect(vehicles.pageInfo).toHaveText("Page 1 of 2");
    await expect(vehicles.previousButton).toBeDisabled();

    await vehicles.nextButton.click();
    await expect(vehicles.modelCells).toHaveText(Array(5).fill(model));
    await expect(vehicles.pageInfo).toHaveText("Page 2 of 2");
    await expect(vehicles.nextButton).toBeDisabled();

    await vehicles.previousButton.click();
    await expect(vehicles.modelCells).toHaveText(Array(20).fill(model));
    await expect(vehicles.pageInfo).toHaveText("Page 1 of 2");
  });

  test("starting a rollout shows it with its status and counts", async ({ page, request }) => {
    const api = new FleetApi(request);
    const model = randomModel();
    await api.registerVehicles(model, 4);
    const release = await api.publishRelease(model);

    const releases = new ReleasesPage(page);
    await releases.open();
    await releases.startRollout(`${release.version} (${model})`, 50);

    const rollout = new RolloutPage(page);
    await expect(page).toHaveURL(/#\/rollouts\/[0-9a-f-]{36}$/);
    await expect(rollout.status).toHaveText("RUNNING");
    await expect(rollout.percentage).toHaveText("50%");
    // 50% of 4 vehicles
    await expect(rollout.pending).toHaveText("2");
    await expect(rollout.installing).toHaveText("0");
    await expect(rollout.succeeded).toHaveText("0");
    await expect(rollout.failed).toHaveText("0");
  });

  test("pausing and resuming a rollout", async ({ page, request }) => {
    const api = new FleetApi(request);
    const model = randomModel();
    await api.registerVehicles(model, 2);
    const release = await api.publishRelease(model);
    const rolloutId = await api.startRollout(release.id, 100);

    const rollout = new RolloutPage(page);
    await rollout.open(rolloutId);
    await expect(rollout.status).toHaveText("RUNNING");

    await rollout.pauseButton.click();
    await expect(rollout.status).toHaveText("PAUSED");
    await expect(rollout.pauseReason).toHaveText("MANUAL");
    await expect(rollout.pauseButton).toBeDisabled();

    await rollout.resumeButton.click();
    await expect(rollout.status).toHaveText("RUNNING");
    await expect(rollout.pauseReason).toHaveText("none");
    await expect(rollout.resumeButton).toBeDisabled();
  });

  test("a rollout that paused itself shows the reason FAILURE_THRESHOLD", async ({ page, request }) => {
    const api = new FleetApi(request);
    const model = randomModel();
    const vehicles = await api.registerVehicles(model, 10);
    const release = await api.publishRelease(model);
    const rolloutId = await api.startRollout(release.id, 100);
    // 2 of 5 finished installations failed: 40%, over the 20% threshold (#6).
    for (const [i, vehicle] of vehicles.slice(0, 5).entries()) {
      await api.install(vehicle, release.version, i < 3);
    }

    const rollout = new RolloutPage(page);
    await rollout.open(rolloutId);

    await expect(rollout.status).toHaveText("PAUSED");
    await expect(rollout.pauseReason).toHaveText("FAILURE_THRESHOLD");
    await expect(rollout.pending).toHaveText("5");
    await expect(rollout.succeeded).toHaveText("3");
    await expect(rollout.failed).toHaveText("2");
  });

  test("an API error is shown to the operator", async ({ page, request }) => {
    const missing = randomUUID();
    const response = await request.get(`/api/v1/rollouts/${missing}`);
    expect(response.status()).toBe(404);
    const { message } = await response.json();
    // fleet-api's own message, not the dashboard's status-line fallback
    expect(message).toContain(missing);

    const rollout = new RolloutPage(page);
    await rollout.open(missing);

    await expect(rollout.error).toHaveText(message);
  });
});
