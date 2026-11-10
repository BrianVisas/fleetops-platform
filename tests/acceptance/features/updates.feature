@issue-5
Feature: Vehicles poll for updates and report the result
  As a vehicle
  I want to ask whether an update is assigned to me and report how the installation went
  So that the fleet operator sees real progress

  Scenario: A targeted vehicle is offered the release
    Given 1 vehicle of a new model on software version "1.0.0"
    And a release of a new version for that model
    And a rollout of the release to 100%
    When vehicle 1 asks for its update
    Then the response status is 200
    And the response offers the release

  Scenario: A vehicle without an assigned update gets nothing
    Given 1 vehicle of a new model on software version "1.0.0"
    When vehicle 1 asks for its update
    Then the response status is 204

  Scenario: A vehicle in a paused rollout gets nothing
    Given 1 vehicle of a new model on software version "1.0.0"
    And a release of a new version for that model
    And a rollout of the release to 100%
    And the rollout is paused
    When vehicle 1 asks for its update
    Then the response status is 204

  Scenario: A successful installation updates the vehicle's software version
    Given 1 vehicle of a new model on software version "1.0.0"
    And a release of a new version for that model
    And a rollout of the release to 100%
    And vehicle 1 has asked for its update
    When vehicle 1 reports that the installation succeeded
    Then the response status is 202
    And vehicle 1 is on the release version
    And the rollout shows 0 pending, 0 installing, 1 succeeded and 0 failed

  Scenario: A failed installation leaves the software version unchanged
    Given 1 vehicle of a new model on software version "1.0.0"
    And a release of a new version for that model
    And a rollout of the release to 100%
    And vehicle 1 has asked for its update
    When vehicle 1 reports that the installation failed
    Then the response status is 202
    And vehicle 1 is still on software version "1.0.0"
    And the rollout shows 0 pending, 0 installing, 0 succeeded and 1 failed
