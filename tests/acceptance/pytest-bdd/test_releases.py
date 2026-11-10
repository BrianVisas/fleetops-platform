"""Steps for features/releases.feature (customer issue #3)."""
import secrets
import uuid

from pytest_bdd import given, parsers, scenarios, then, when

from conftest import random_model, random_version

scenarios("releases.feature")


def publish(api, world, key, model=None, version=None, size_bytes=1024):
    response = api.publish_release(version or random_version(), model or random_model(),
                                   secrets.token_hex(32), size_bytes)
    world["response"] = response
    if response.status_code == 201:
        world[key] = response.json()
    return response


# ---- Given ----

@given("a published release")
def a_published_release(api, world):
    assert publish(api, world, "release").status_code == 201, world["response"].text


@given("a second release published after it")
def a_second_release(api, world):
    assert publish(api, world, "second").status_code == 201, world["response"].text


# ---- When ----

@when(parsers.parse('I publish a release of a new version for model "{model}"'))
def publish_for_model(api, world, model):
    world["requested_model"] = model
    publish(api, world, "release", model=model)


@when("I publish the same version again")
def publish_again(api, world):
    world["response"] = api.publish_release(world["release"]["version"], random_model(),
                                            secrets.token_hex(32), 1024)


@when(parsers.parse("I publish a release with a size of {size:d} bytes"))
def publish_with_size(api, world, size):
    publish(api, world, "release", size_bytes=size)


@when("I list the releases")
def list_releases(api, world):
    world["response"] = api.list_releases()


# ---- Then ----

@then("the response contains the release with an id")
def response_has_release(world):
    body = world["response"].json()
    uuid.UUID(body["id"])
    assert body["targetModel"] == world["requested_model"]
    assert world["response"].headers["Location"].endswith("/api/v1/releases/" + body["id"])


@then("the second release is listed before the first")
def second_before_first(world):
    ids = [release["id"] for release in world["response"].json()]
    assert ids.index(world["second"]["id"]) < ids.index(world["release"]["id"]), ids
