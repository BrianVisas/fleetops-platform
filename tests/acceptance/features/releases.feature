@issue-3
Feature: Publish a software release
  As a fleet operator
  I want to publish a software release
  So that it can be rolled out to vehicles

  Scenario: Publishing a release
    When I publish a release of a new version for model "ID.4"
    Then the response status is 201
    And the response contains the release with an id

  Scenario: Publishing a version that already exists is rejected
    Given a published release
    When I publish the same version again
    Then the response status is 409

  Scenario: A release must have a positive size
    When I publish a release with a size of 0 bytes
    Then the response status is 400
    And the error message mentions "sizeBytes"

  Scenario: Releases are listed newest first
    Given a published release
    And a second release published after it
    When I list the releases
    Then the response status is 200
    And the second release is listed before the first
