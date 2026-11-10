"""Shared fixtures: where the system under test is, and per-scenario state."""
import os
import random
import string

import pytest
import requests

FLEET_API_URL = os.environ.get("FLEET_API_URL", "http://localhost:8080").rstrip("/")

# Digits and capitals without I, O and Q: the characters a real VIN may use.
VIN_CHARS = "".join(c for c in string.ascii_uppercase + string.digits if c not in "IOQ")


def random_vin():
    return "".join(random.choices(VIN_CHARS, k=17))


def random_model():
    # A model name no other scenario uses, so list filters see only this
    # scenario's vehicles even though the database is shared and never reset.
    return "AT-" + "".join(random.choices(string.ascii_uppercase + string.digits, k=8))


class FleetApi:
    """Thin client for fleet-api. Every call returns the raw response, so the
    steps decide what counts as success."""

    def __init__(self):
        self.http = requests.Session()
        self.http.headers["Accept"] = "application/json"

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


@pytest.fixture
def api():
    return FleetApi()


@pytest.fixture
def world():
    """What one scenario remembers between its steps."""
    return {}
