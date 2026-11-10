"""Shared fixtures and steps: where the system under test is, per-scenario
state, and the steps more than one feature uses."""
import os
import random
import string
from datetime import datetime, timedelta, timezone

import pytest
import requests
from pytest_bdd import given, parsers, then

# Steps shared by the rollout and update features live in their own module.
pytest_plugins = ["rollout_steps"]

FLEET_API_URL = os.environ.get("FLEET_API_URL", "http://localhost:8080").rstrip("/")
TELEMETRY_URL = os.environ.get("TELEMETRY_URL", "http://localhost:8081").rstrip("/")

# Digits and capitals without I, O and Q: the characters a real VIN may use.
VIN_CHARS = "".join(c for c in string.ascii_uppercase + string.digits if c not in "IOQ")


def random_vin():
    return "".join(random.choices(VIN_CHARS, k=17))


def random_version():
    # Release versions are unique across the whole system, so every scenario
    # publishes its own.
    return "9." + "".join(random.choices(string.digits, k=9))


def random_model():
    # A model name no other scenario uses, so list filters see only this
    # scenario's vehicles even though the database is shared and never reset.
    return "AT-" + "".join(random.choices(string.ascii_uppercase + string.digits, k=8))


class FleetApi:
    """Thin client for fleet-api. Every call returns the raw response, so the
    steps decide what counts as success."""

    def __init__(self, http):
        self.http = http

    def register(self, vin, model, sw_version):
        return self.http.post(f"{FLEET_API_URL}/api/v1/vehicles", timeout=10,
                              json={"vin": vin, "model": model, "swVersion": sw_version})

    def get(self, vehicle_id):
        return self.http.get(f"{FLEET_API_URL}/api/v1/vehicles/{vehicle_id}", timeout=10)

    def update(self, vehicle_id, model, sw_version):
        return self.http.put(f"{FLEET_API_URL}/api/v1/vehicles/{vehicle_id}", timeout=10,
                             json={"model": model, "swVersion": sw_version})

    def delete(self, vehicle_id):
        return self.http.delete(f"{FLEET_API_URL}/api/v1/vehicles/{vehicle_id}", timeout=10)

    def list(self, model, page, size):
        return self.http.get(f"{FLEET_API_URL}/api/v1/vehicles", timeout=10,
                             params={"model": model, "page": page, "size": size})

    def publish_release(self, version, target_model, checksum, size_bytes):
        return self.http.post(f"{FLEET_API_URL}/api/v1/releases", timeout=10,
                              json={"version": version, "targetModel": target_model,
                                    "checksum": checksum, "sizeBytes": size_bytes})

    def list_releases(self):
        return self.http.get(f"{FLEET_API_URL}/api/v1/releases", timeout=10)

    def create_rollout(self, release_id, percentage):
        return self.http.post(f"{FLEET_API_URL}/api/v1/rollouts", timeout=10,
                              json={"releaseId": release_id, "percentage": percentage})

    def get_rollout(self, rollout_id):
        return self.http.get(f"{FLEET_API_URL}/api/v1/rollouts/{rollout_id}", timeout=10)

    def change_percentage(self, rollout_id, percentage):
        return self.http.post(f"{FLEET_API_URL}/api/v1/rollouts/{rollout_id}/percentage", timeout=10,
                              json={"percentage": percentage})

    def pause(self, rollout_id):
        return self.http.post(f"{FLEET_API_URL}/api/v1/rollouts/{rollout_id}/pause", timeout=10)

    def claim_update(self, vehicle_id):
        return self.http.get(f"{FLEET_API_URL}/api/v1/vehicles/{vehicle_id}/update", timeout=10)

    def report_result(self, vehicle_id, version, success):
        return self.http.post(f"{FLEET_API_URL}/api/v1/vehicles/{vehicle_id}/update/result", timeout=10,
                              json={"version": version, "success": success})


class TelemetryApi:
    """Thin client for telemetry-ingest."""

    def __init__(self, http):
        self.http = http

    @staticmethod
    def reading(vehicle_id, speed=50.0, minutes_ago=0):
        recorded_at = datetime.now(timezone.utc) - timedelta(minutes=minutes_ago)
        return {
            "vehicleId": vehicle_id,
            "recordedAt": recorded_at.isoformat().replace("+00:00", "Z"),
            "speedKmh": speed,
            "batteryTemp": 25.0,
            "voltage": 400.0,
        }

    def send(self, body):
        return self.http.post(f"{TELEMETRY_URL}/telemetry", json=body, timeout=10)

    def latest(self, vehicle_id):
        return self.http.get(f"{TELEMETRY_URL}/vehicles/{vehicle_id}/latest", timeout=10)


@pytest.fixture
def http():
    session = requests.Session()
    session.headers["Accept"] = "application/json"
    return session


@pytest.fixture
def api(http):
    return FleetApi(http)


@pytest.fixture
def telemetry(http):
    return TelemetryApi(http)


@pytest.fixture
def world():
    """What one scenario remembers between its steps."""
    return {}


def register(api, world, model="ID.4", sw_version="1.0.0", vin=None):
    vin = vin or random_vin()
    response = api.register(vin, model, sw_version)
    world["response"] = response
    world["vehicle"] = {"vin": vin, "model": model, "swVersion": sw_version}
    return response


def pytest_bdd_step_error(request, feature, scenario, step, step_func, step_func_args, exception):
    """Print the last HTTP exchange when a step fails. pytest captures it, and
    the HTML report shows it next to the failure."""
    response = request.getfixturevalue("world").get("response")
    if response is not None:
        print(f"--- last request: {response.request.method} {response.request.url}")
        print(f"{response.request.body!r}")
        print(f"--- last response: {response.status_code}")
        print(response.text)


# ---- Steps used by more than one feature ----

@given("a registered vehicle")
def a_registered_vehicle(api, world):
    response = register(api, world)
    assert response.status_code == 201, response.text
    world["vehicle"]["id"] = response.json()["id"]


@then(parsers.parse("the response status is {status:d}"))
def response_status(world, status):
    response = world["response"]
    assert response.status_code == status, f"{response.status_code}: {response.text}"


@then(parsers.parse('the error message mentions "{text}"'))
def error_mentions(world, text):
    message = world["response"].json()["message"]
    assert text in message, message
