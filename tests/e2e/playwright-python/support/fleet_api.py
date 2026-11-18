"""Sets up test data through fleet-api, so each test creates exactly the state it needs."""
import secrets
import string

from playwright.sync_api import APIRequestContext, expect

# Digits and capitals without I, O and Q: the characters a real VIN may use.
VIN_CHARS = "".join(c for c in string.ascii_uppercase + string.digits if c not in "IOQ")


def pick(chars, length):
    return "".join(secrets.choice(chars) for _ in range(length))


def random_model():
    # A model no other test uses, so a filter on it sees only this test's
    # vehicles even though the database is shared and never reset.
    return "UI-" + pick(VIN_CHARS, 8)


class FleetApi:
    def __init__(self, request: APIRequestContext):
        self.request = request

    def register_vehicles(self, model, count):
        """Registers vehicles of one model and returns their ids, in order."""
        ids = []
        for _ in range(count):
            response = self.request.post("/api/v1/vehicles", data={
                "vin": pick(VIN_CHARS, 17), "model": model, "swVersion": "1.0.0"})
            expect(response).to_be_ok()
            ids.append(response.json()["id"])
        return ids

    def publish_release(self, model):
        response = self.request.post("/api/v1/releases", data={
            "version": "8." + pick(string.digits, 9),
            "targetModel": model,
            "checksum": secrets.token_hex(32),
            "sizeBytes": 1024,
        })
        expect(response).to_be_ok()
        return response.json()

    def start_rollout(self, release_id, percentage):
        """Starts a rollout and returns its id."""
        response = self.request.post("/api/v1/rollouts", data={
            "releaseId": release_id, "percentage": percentage})
        expect(response).to_be_ok()
        return response.json()["id"]

    def install(self, vehicle_id, version, success):
        """The vehicle asks for its update, then reports how the installation went."""
        claim = self.request.get(f"/api/v1/vehicles/{vehicle_id}/update")
        assert claim.status == 200, claim.text()
        result = self.request.post(f"/api/v1/vehicles/{vehicle_id}/update/result",
                                   data={"version": version, "success": success})
        assert result.status == 202, result.text()

    def get(self, path):
        return self.request.get(path)
