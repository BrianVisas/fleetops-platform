"""Steps shared by features/rollouts.feature (#4) and features/updates.feature (#5)."""
import secrets

from pytest_bdd import given, parsers, then, when

from conftest import random_model, random_version, random_vin


def publish(api, world, model):
    version = random_version()
    response = api.publish_release(version, model, secrets.token_hex(32), 1024)
    assert response.status_code == 201, response.text
    world["release"] = response.json()


def vehicle_id(world, number):
    """Vehicles are numbered from 1 in the order the scenario registered them."""
    return world["vehicles"][number - 1]


def counts(api, world):
    response = api.get_rollout(world["rollout_id"])
    assert response.status_code == 200, response.text
    return response.json()


# ---- Given ----

@given(parsers.re(r"(?P<count>\d+) vehicles? of a new model on software version \"(?P<version>[^\"]+)\""))
def vehicles_of_new_model(api, world, count, version):
    world["model"], world["vehicles"] = random_model(), []
    for _ in range(int(count)):
        response = api.register(random_vin(), world["model"], version)
        assert response.status_code == 201, response.text
        world["vehicles"].append(response.json()["id"])


@given("a release of a new version for that model")
def release_for_model(api, world):
    publish(api, world, world["model"])


@given(parsers.re(r"(?P<count>\d+) vehicles? of that model already on the release version"))
def vehicles_on_release_version(api, world, count):
    for _ in range(int(count)):
        response = api.register(random_vin(), world["model"], world["release"]["version"])
        assert response.status_code == 201, response.text


@given(parsers.re(r"(?P<count>\d+) vehicles? of another model"))
def vehicles_of_another_model(api, count):
    model = random_model()
    for _ in range(int(count)):
        response = api.register(random_vin(), model, "1.0.0")
        assert response.status_code == 201, response.text


@given(parsers.parse("a rollout of the release to {percentage:d}%"))
def rollout_exists(api, world, percentage):
    response = api.create_rollout(world["release"]["id"], percentage)
    assert response.status_code == 201, response.text
    world["rollout_id"] = response.json()["id"]


@given("the rollout is paused")
def rollout_is_paused(api, world):
    response = api.pause(world["rollout_id"])
    assert response.status_code == 200, response.text


@given("every vehicle of that model has asked for its update")
def every_vehicle_asked(api, world):
    for vid in world["vehicles"]:
        response = api.claim_update(vid)
        assert response.status_code in (200, 204), response.text


@given(parsers.parse("vehicle {number:d} has asked for its update"))
def vehicle_has_asked(api, world, number):
    response = api.claim_update(vehicle_id(world, number))
    assert response.status_code == 200, response.text


@given(parsers.parse("vehicle {number:d} has installed the update successfully"))
def vehicle_installed(api, world, number):
    vehicle_has_asked(api, world, number)
    response = api.report_result(vehicle_id(world, number), world["release"]["version"], True)
    assert response.status_code == 202, response.text


@given(parsers.parse("vehicle {number:d} has failed to install the update"))
def vehicle_failed(api, world, number):
    vehicle_has_asked(api, world, number)
    response = api.report_result(vehicle_id(world, number), world["release"]["version"], False)
    assert response.status_code == 202, response.text


# ---- When ----

@when(parsers.parse("I create a rollout of the release to {percentage:d}%"))
def create_rollout(api, world, percentage):
    world["response"] = api.create_rollout(world["release"]["id"], percentage)
    if world["response"].status_code == 201:
        world["rollout_id"] = world["response"].json()["id"]


@when(parsers.parse("I raise the rollout to {percentage:d}%"))
def raise_rollout(api, world, percentage):
    world["response"] = api.change_percentage(world["rollout_id"], percentage)


@when("I pause the rollout")
def pause_rollout(api, world):
    world["response"] = api.pause(world["rollout_id"])


@when("I look at the rollout")
def look_at_rollout(api, world):
    world["response"] = api.get_rollout(world["rollout_id"])


@when(parsers.parse("vehicle {number:d} asks for its update"))
def vehicle_asks(api, world, number):
    world["response"] = api.claim_update(vehicle_id(world, number))


@when(parsers.re(r"vehicle (?P<number>\d+) reports that the installation (?P<outcome>succeeded|failed)"))
def vehicle_reports(api, world, number, outcome):
    world["response"] = api.report_result(
        vehicle_id(world, int(number)), world["release"]["version"], outcome == "succeeded")


# ---- Then ----

@then(parsers.re(r"the rollout targets (?P<count>\d+) vehicles?"))
def rollout_targets(api, world, count):
    body = counts(api, world)
    total = body["pending"] + body["installing"] + body["succeeded"] + body["failed"]
    assert total == int(count), body


@then(parsers.parse("the rollout shows {pending:d} pending, {installing:d} installing, "
                    "{succeeded:d} succeeded and {failed:d} failed"))
def rollout_shows(api, world, pending, installing, succeeded, failed):
    body = counts(api, world)
    actual = (body["pending"], body["installing"], body["succeeded"], body["failed"])
    assert actual == (pending, installing, succeeded, failed), body


@then(parsers.parse("the rollout status is {status}"))
def rollout_status(api, world, status):
    assert counts(api, world)["status"] == status


@then("the response offers the release")
def response_offers_release(world):
    body, release = world["response"].json(), world["release"]
    assert (body["version"], body["checksum"], body["sizeBytes"]) == \
        (release["version"], release["checksum"], release["sizeBytes"])


@then(parsers.parse("vehicle {number:d} is on the release version"))
def vehicle_on_release(api, world, number):
    response = api.get(vehicle_id(world, number))
    assert response.json()["swVersion"] == world["release"]["version"]


@then(parsers.parse('vehicle {number:d} is still on software version "{version}"'))
def vehicle_still_on(api, world, number, version):
    response = api.get(vehicle_id(world, number))
    assert response.json()["swVersion"] == version
