*** Settings ***
Documentation       Customer issue #6: auto-pause a rollout when failures pass a threshold.
...                 As a fleet operator I want a rollout to pause itself when too many
...                 installations fail, so that a bad release stops spreading without
...                 me watching it. The threshold is 20% of finished installations,
...                 not applied until at least 5 have finished. Same scenarios as
...                 features/auto_pause.feature.
Resource            rollout_steps.resource
Suite Setup         Connect To Fleet Api
Test Tags           issue-6


*** Test Cases ***
A rollout pauses itself when more than 20% of finished installations failed
    Given 10 vehicles of a new model on software version "1.0.0"
    And a release of a new version for that model
    And a rollout of the release to 100%
    And vehicles 1 to 3 have installed the update successfully
    And vehicle 4 has failed to install the update
    And vehicle 5 has asked for its update
    When vehicle 5 reports that the installation failed
    Then the response status is 202
    And the rollout status is PAUSED
    And the rollout's pause reason is FAILURE_THRESHOLD

Vehicles get no update once the rollout has paused itself
    Given 10 vehicles of a new model on software version "1.0.0"
    And a release of a new version for that model
    And a rollout of the release to 100%
    And vehicles 1 to 3 have installed the update successfully
    And vehicles 4 to 5 have failed to install the update
    When vehicle 6 asks for its update
    Then the response status is 204

The threshold is not applied before 5 installations have finished
    Given 10 vehicles of a new model on software version "1.0.0"
    And a release of a new version for that model
    And a rollout of the release to 100%
    And vehicles 1 to 4 have failed to install the update
    When I look at the rollout
    Then the response status is 200
    And the rollout status is RUNNING

Exactly 20% failed installations does not pause the rollout
    Given 10 vehicles of a new model on software version "1.0.0"
    And a release of a new version for that model
    And a rollout of the release to 100%
    And vehicles 1 to 4 have installed the update successfully
    And vehicle 5 has failed to install the update
    When I look at the rollout
    Then the response status is 200
    And the rollout status is RUNNING

A rollout paused by the operator says so
    Given 2 vehicles of a new model on software version "1.0.0"
    And a release of a new version for that model
    And a rollout of the release to 100%
    When I pause the rollout
    Then the response status is 200
    And the rollout's pause reason is MANUAL
