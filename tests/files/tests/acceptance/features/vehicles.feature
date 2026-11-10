@issue-1
Feature: Register and manage vehicles
  As a fleet operator
  I want to register, list, update and delete vehicles
  So that the fleet in the system matches the fleet on the road

  Scenario: Registering a new vehicle
    When I register a vehicle of model "ID.4" with software version "1.0.0"
    Then the response status is 201
    And the response contains the new vehicle with an id

  Scenario: Registering a VIN that already exists is rejected
    Given a registered vehicle
    When I register another vehicle with the same VIN
    Then the response status is 409

  Scenario: Registering a vehicle with an invalid VIN is rejected
    When I register a vehicle with the VIN "TOOSHORT"
    Then the response status is 400
    And the error message mentions "vin"

  Scenario: Reading a stored vehicle
    Given a registered vehicle
    When I fetch that vehicle
    Then the response status is 200
    And the response shows the vehicle's VIN, model and software version

  Scenario: Updating a vehicle
    Given a registered vehicle
    When I update its software version to "2.0.0"
    Then the response status is 200
    And fetching the vehicle shows software version "2.0.0"

  Scenario: Deleting a vehicle
    Given a registered vehicle
    When I delete that vehicle
    Then the response status is 204
    And fetching the vehicle returns status 404

  Scenario Outline: Operations on a vehicle that does not exist
    When I <operation> a vehicle that does not exist
    Then the response status is 404

    Examples:
      | operation |
      | fetch     |
      | update    |
      | delete    |

  Scenario: Listing vehicles of one model, page by page
    Given 3 registered vehicles of one model and 2 of another
    When I list vehicles of the first model, 2 per page
    Then page 1 holds 2 vehicles, all of the first model
    And page 2 holds 1 vehicle, all of the first model
