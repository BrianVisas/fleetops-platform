"""Issue #40: see the fleet and steer rollouts in a browser. One test per
acceptance criterion, with the same names in the TypeScript and Java suites."""
import re
import uuid

import pytest
from playwright.sync_api import Page, expect

from pages.releases_page import ReleasesPage
from pages.rollout_page import RolloutPage
from pages.vehicles_page import VehiclesPage
from support.fleet_api import random_model

pytestmark = pytest.mark.issue(40)


def test_filtering_by_model_shows_only_that_model_20_per_page(page: Page, api):
    model = random_model()
    api.register_vehicles(model, 25)

    vehicles = VehiclesPage(page)
    vehicles.open()
    vehicles.filter_by_model(model)

    expect(vehicles.model_cells).to_have_text([model] * 20)
    expect(vehicles.page_info).to_have_text("Page 1 of 2")
    expect(vehicles.previous_button).to_be_disabled()

    vehicles.next_button.click()
    expect(vehicles.model_cells).to_have_text([model] * 5)
    expect(vehicles.page_info).to_have_text("Page 2 of 2")
    expect(vehicles.next_button).to_be_disabled()

    vehicles.previous_button.click()
    expect(vehicles.model_cells).to_have_text([model] * 20)
    expect(vehicles.page_info).to_have_text("Page 1 of 2")


def test_starting_a_rollout_shows_it_with_its_status_and_counts(page: Page, api):
    model = random_model()
    api.register_vehicles(model, 4)
    release = api.publish_release(model)

    releases = ReleasesPage(page)
    releases.open()
    releases.start_rollout(f"{release['version']} ({model})", 50)

    rollout = RolloutPage(page)
    expect(page).to_have_url(re.compile(r"#/rollouts/[0-9a-f-]{36}$"))
    expect(rollout.status).to_have_text("RUNNING")
    expect(rollout.percentage).to_have_text("50%")
    # 50% of 4 vehicles
    expect(rollout.pending).to_have_text("2")
    expect(rollout.installing).to_have_text("0")
    expect(rollout.succeeded).to_have_text("0")
    expect(rollout.failed).to_have_text("0")


def test_pausing_and_resuming_a_rollout(page: Page, api):
    model = random_model()
    api.register_vehicles(model, 2)
    release = api.publish_release(model)
    rollout_id = api.start_rollout(release["id"], 100)

    rollout = RolloutPage(page)
    rollout.open(rollout_id)
    expect(rollout.status).to_have_text("RUNNING")

    rollout.pause_button.click()
    expect(rollout.status).to_have_text("PAUSED")
    expect(rollout.pause_reason).to_have_text("MANUAL")
    expect(rollout.pause_button).to_be_disabled()

    rollout.resume_button.click()
    expect(rollout.status).to_have_text("RUNNING")
    expect(rollout.pause_reason).to_have_text("none")
    expect(rollout.resume_button).to_be_disabled()


def test_a_rollout_that_paused_itself_shows_the_reason_failure_threshold(page: Page, api):
    model = random_model()
    vehicles = api.register_vehicles(model, 10)
    release = api.publish_release(model)
    rollout_id = api.start_rollout(release["id"], 100)
    # 2 of 5 finished installations failed: 40%, over the 20% threshold (#6).
    for i, vehicle in enumerate(vehicles[:5]):
        api.install(vehicle, release["version"], success=i < 3)

    rollout = RolloutPage(page)
    rollout.open(rollout_id)

    expect(rollout.status).to_have_text("PAUSED")
    expect(rollout.pause_reason).to_have_text("FAILURE_THRESHOLD")
    expect(rollout.pending).to_have_text("5")
    expect(rollout.succeeded).to_have_text("3")
    expect(rollout.failed).to_have_text("2")


def test_an_api_error_is_shown_to_the_operator(page: Page, api):
    missing = str(uuid.uuid4())
    response = api.get(f"/api/v1/rollouts/{missing}")
    assert response.status == 404, response.text()
    message = response.json()["message"]
    # fleet-api's own message, not the dashboard's status-line fallback
    assert missing in message

    rollout = RolloutPage(page)
    rollout.open(missing)

    expect(rollout.error).to_have_text(message)
