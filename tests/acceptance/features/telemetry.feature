@issue-2
Feature: Ingest vehicle telemetry
  As a fleet operator
  I want vehicles to send telemetry continuously
  So that I can see the current state of each vehicle

  Scenario: A registered vehicle sends a reading
    Given a registered vehicle
    When the vehicle sends a reading with speed 88.5 km/h
    Then the response status is 202
    And the vehicle's latest reading shows speed 88.5 km/h

  Scenario: A reading from an unknown vehicle is rejected and not stored
    When a vehicle that is not registered sends a reading
    Then the response status is 400
    And the error message mentions "vehicleId"
    And that vehicle has no latest reading

  Scenario Outline: A reading with a missing or non-numeric field is rejected
    Given a registered vehicle
    When the vehicle sends a reading where <field> is <problem>
    Then the response status is 400
    And the error message mentions "<field>"

    Examples:
      | field       | problem     |
      | speedKmh    | missing     |
      | batteryTemp | missing     |
      | voltage     | non-numeric |

  Scenario: The latest reading is the newest one, not the last one sent
    Given a registered vehicle
    And the vehicle sent a reading 2 minutes ago with speed 50 km/h
    And the vehicle sent a reading 5 minutes ago with speed 30 km/h
    When I ask for the vehicle's latest reading
    Then the response status is 200
    And the reading shows speed 50 km/h

  Scenario: A vehicle with no readings has no latest reading
    Given a registered vehicle
    When I ask for the vehicle's latest reading
    Then the response status is 404
