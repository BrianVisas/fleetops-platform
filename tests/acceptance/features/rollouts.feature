@issue-4
Feature: Start and pause a staged rollout
  As a fleet operator
  I want to roll a release out to a share of the fleet and pause it
  So that a bad release does not reach every vehicle

  Scenario: Only vehicles of the release's model that are not on its version are targeted
    Given 3 vehicles of a new model on software version "1.0.0"
    And a release of a new version for that model
    And 1 vehicle of that model already on the release version
    And 2 vehicles of another model
    When I create a rollout of the release to 100%
    Then the response status is 201
    And the rollout targets 3 vehicles

  Scenario Outline: The share of vehicles targeted is rounded up
    Given <vehicles> vehicles of a new model on software version "1.0.0"
    And a release of a new version for that model
    When I create a rollout of the release to <percentage>%
    Then the response status is 201
    And the rollout targets <targeted> vehicles

    Examples:
      | vehicles | percentage | targeted |
      | 10       | 10         | 1        |
      | 10       | 15         | 2        |
      | 3        | 50         | 2        |

  Scenario: Raising the percentage adds vehicles and keeps existing targets as they are
    Given 10 vehicles of a new model on software version "1.0.0"
    And a release of a new version for that model
    And a rollout of the release to 10%
    And every vehicle of that model has asked for its update
    When I raise the rollout to 50%
    Then the response status is 200
    And the rollout shows 4 pending, 1 installing, 0 succeeded and 0 failed

  Scenario: Pausing a rollout
    Given 2 vehicles of a new model on software version "1.0.0"
    And a release of a new version for that model
    And a rollout of the release to 100%
    When I pause the rollout
    Then the response status is 200
    And the rollout status is PAUSED

  Scenario: A rollout shows how many vehicles are in each state
    Given 4 vehicles of a new model on software version "1.0.0"
    And a release of a new version for that model
    And a rollout of the release to 100%
    And vehicle 1 has installed the update successfully
    And vehicle 2 has failed to install the update
    And vehicle 3 has asked for its update
    When I look at the rollout
    Then the response status is 200
    And the rollout shows 1 pending, 1 installing, 1 succeeded and 1 failed
