*** Settings ***
Documentation       Customer issue #4: start and pause a staged rollout.
...                 As a fleet operator I want to roll a release out to a share of
...                 the fleet and pause it, so that a bad release does not reach
...                 every vehicle. Same scenarios as features/rollouts.feature.
Resource            rollout_steps.resource
Suite Setup         Connect To Fleet Api
Test Tags           issue-4


*** Test Cases ***
Only vehicles of the release's model that are not on its version are targeted
    Given 3 vehicles of a new model on software version "1.0.0"
    And a release of a new version for that model
    And 1 vehicle of that model already on the release version
    And 2 vehicles of another model
    When I create a rollout of the release to 100%
    Then the response status is 201
    And the rollout targets 3 vehicles

The share of vehicles targeted is rounded up
    [Template]    A rollout of ${percentage}% of ${vehicles} vehicles targets ${targeted}
    10    10    1
    15    10    2
    50    3     2

Raising the percentage adds vehicles and keeps existing targets as they are
    Given 10 vehicles of a new model on software version "1.0.0"
    And a release of a new version for that model
    And a rollout of the release to 10%
    And every vehicle of that model has asked for its update
    When I raise the rollout to 50%
    Then the response status is 200
    And the rollout shows 4 pending, 1 installing, 0 succeeded and 0 failed

Pausing a rollout
    Given 2 vehicles of a new model on software version "1.0.0"
    And a release of a new version for that model
    And a rollout of the release to 100%
    When I pause the rollout
    Then the response status is 200
    And the rollout status is PAUSED

A rollout shows how many vehicles are in each state
    Given 4 vehicles of a new model on software version "1.0.0"
    And a release of a new version for that model
    And a rollout of the release to 100%
    And vehicle 1 has installed the update successfully
    And vehicle 2 has failed to install the update
    And vehicle 3 has asked for its update
    When I look at the rollout
    Then the response status is 200
    And the rollout shows 1 pending, 1 installing, 1 succeeded and 1 failed


*** Keywords ***
A rollout of ${percentage}% of ${vehicles} vehicles targets ${targeted}
    ${vehicles} vehicles of a new model on software version "1.0.0"
    A release of a new version for that model
    I create a rollout of the release to ${percentage}%
    The response status is 201
    The rollout targets ${targeted} vehicles
