"""Steps for features/telemetry.feature (customer issue #2)."""
import uuid

from pytest_bdd import given, parsers, scenarios, then, when

scenarios("telemetry.feature")


# ---- Given ----

@given(parsers.parse("the vehicle sent a reading {minutes:d} minutes ago with speed {speed:g} km/h"))
def vehicle_sent_reading(telemetry, world, minutes, speed):
    body = telemetry.reading(world["vehicle"]["id"], speed=speed, minutes_ago=minutes)
    response = telemetry.send(body)
    assert response.status_code == 202, response.text


# ---- When ----

@when(parsers.parse("the vehicle sends a reading with speed {speed:g} km/h"))
def vehicle_sends_reading(telemetry, world, speed):
    world["response"] = telemetry.send(telemetry.reading(world["vehicle"]["id"], speed=speed))


@when("a vehicle that is not registered sends a reading")
def unknown_vehicle_sends_reading(telemetry, world):
    world["unknown_id"] = str(uuid.uuid4())
    world["response"] = telemetry.send(telemetry.reading(world["unknown_id"]))


@when(parsers.parse("the vehicle sends a reading where {field} is {problem}"))
def vehicle_sends_bad_reading(telemetry, world, field, problem):
    body = telemetry.reading(world["vehicle"]["id"])
    if problem == "missing":
        del body[field]
    elif problem == "non-numeric":
        body[field] = "not-a-number"
    else:
        raise ValueError(f"unknown problem: {problem}")
    world["response"] = telemetry.send(body)


@when("I ask for the vehicle's latest reading")
def ask_latest(telemetry, world):
    world["response"] = telemetry.latest(world["vehicle"]["id"])


# ---- Then ----

@then(parsers.parse("the vehicle's latest reading shows speed {speed:g} km/h"))
def latest_shows_speed(telemetry, world, speed):
    response = telemetry.latest(world["vehicle"]["id"])
    assert response.status_code == 200, response.text
    assert response.json()["speedKmh"] == speed


@then("that vehicle has no latest reading")
def unknown_has_no_reading(telemetry, world):
    response = telemetry.latest(world["unknown_id"])
    assert response.status_code == 404, response.text


@then(parsers.parse("the reading shows speed {speed:g} km/h"))
def reading_shows_speed(world, speed):
    assert world["response"].json()["speedKmh"] == speed
