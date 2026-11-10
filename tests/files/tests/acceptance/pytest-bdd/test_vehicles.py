"""Steps for features/vehicles.feature (customer issue #1)."""
import uuid

from pytest_bdd import given, parsers, scenarios, then, when

from conftest import random_model, random_vin

scenarios("vehicles.feature")


def register(api, world, model="ID.4", sw_version="1.0.0", vin=None):
    vin = vin or random_vin()
    response = api.register(vin, model, sw_version)
    world["response"] = response
    world["vehicle"] = {"vin": vin, "model": model, "swVersion": sw_version}
    return response


# ---- Given ----

@given("a registered vehicle")
def a_registered_vehicle(api, world):
    response = register(api, world)
    assert response.status_code == 201, response.text
    world["vehicle"]["id"] = response.json()["id"]


@given(parsers.parse("{first:d} registered vehicles of one model and {second:d} of another"))
def vehicles_of_two_models(api, world, first, second):
    world["first_model"], other_model = random_model(), random_model()
    for model, count in ((world["first_model"], first), (other_model, second)):
        for _ in range(count):
            response = register(api, world, model=model)
            assert response.status_code == 201, response.text


# ---- When ----

@when(parsers.parse('I register a vehicle of model "{model}" with software version "{sw_version}"'))
def register_vehicle(api, world, model, sw_version):
    register(api, world, model=model, sw_version=sw_version)


@when("I register another vehicle with the same VIN")
def register_same_vin(api, world):
    vehicle = world["vehicle"]
    world["response"] = api.register(vehicle["vin"], "ID.3", "1.0.0")


@when(parsers.parse('I register a vehicle with the VIN "{vin}"'))
def register_with_vin(api, world, vin):
    register(api, world, vin=vin)


@when("I fetch that vehicle")
def fetch_vehicle(api, world):
    world["response"] = api.get(world["vehicle"]["id"])


@when(parsers.parse('I update its software version to "{sw_version}"'))
def update_vehicle(api, world, sw_version):
    vehicle = world["vehicle"]
    world["response"] = api.update(vehicle["id"], vehicle["model"], sw_version)


@when("I delete that vehicle")
def delete_vehicle(api, world):
    world["response"] = api.delete(world["vehicle"]["id"])


@when(parsers.parse("I {operation} a vehicle that does not exist"))
def operate_on_missing_vehicle(api, world, operation):
    missing = str(uuid.uuid4())
    calls = {
        "fetch": lambda: api.get(missing),
        "update": lambda: api.update(missing, "ID.4", "1.0.0"),
        "delete": lambda: api.delete(missing),
    }
    world["response"] = calls[operation]()


@when(parsers.parse("I list vehicles of the first model, {size:d} per page"))
def list_first_model(world, size):
    world["page_size"] = size


# ---- Then ----

@then(parsers.parse("the response status is {status:d}"))
def response_status(world, status):
    response = world["response"]
    assert response.status_code == status, f"{response.status_code}: {response.text}"


@then("the response contains the new vehicle with an id")
def response_has_new_vehicle(world):
    body = world["response"].json()
    uuid.UUID(body["id"])
    assert body["vin"] == world["vehicle"]["vin"]
    assert world["response"].headers["Location"].endswith("/api/v1/vehicles/" + body["id"])


@then(parsers.parse('the error message mentions "{text}"'))
def error_mentions(world, text):
    message = world["response"].json()["message"]
    assert text in message, message


@then("the response shows the vehicle's VIN, model and software version")
def response_shows_vehicle(world):
    body, expected = world["response"].json(), world["vehicle"]
    assert (body["vin"], body["model"], body["swVersion"]) == \
        (expected["vin"], expected["model"], expected["swVersion"])


@then(parsers.parse('fetching the vehicle shows software version "{sw_version}"'))
def fetched_version(api, world, sw_version):
    response = api.get(world["vehicle"]["id"])
    assert response.status_code == 200, response.text
    assert response.json()["swVersion"] == sw_version


@then(parsers.parse("fetching the vehicle returns status {status:d}"))
def fetched_status(api, world, status):
    response = api.get(world["vehicle"]["id"])
    assert response.status_code == status, response.text


@then(parsers.re(r"page (?P<page>\d+) holds (?P<count>\d+) vehicles?, all of the first model"))
def page_holds(api, world, page, count):
    response = api.list(world["first_model"], int(page) - 1, world["page_size"])
    assert response.status_code == 200, response.text
    models = [vehicle["model"] for vehicle in response.json()["content"]]
    assert models == [world["first_model"]] * int(count), models
