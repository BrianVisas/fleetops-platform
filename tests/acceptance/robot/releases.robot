*** Settings ***
Documentation       Customer issue #3: publish a software release.
...                 As a fleet operator I want to publish a software release, so
...                 that it can be rolled out to vehicles. Same scenarios as
...                 features/releases.feature.
Resource            common_steps.resource
Suite Setup         Connect To Fleet Api
Test Tags           issue-3


*** Test Cases ***
Publishing a release
    When I publish a release of a new version for model "ID.4"
    Then the response status is 201
    And the response contains the release with an id

Publishing a version that already exists is rejected
    Given a published release
    When I publish the same version again
    Then the response status is 409

A release must have a positive size
    When I publish a release with a size of 0 bytes
    Then the response status is 400
    And the error message mentions "sizeBytes"

Releases are listed newest first
    Given a published release
    And a second release published after it
    When I list the releases
    Then the response status is 200
    And the second release is listed before the first


*** Keywords ***
A published release
    ${version}=    Random Version
    ${model}=    Random Model
    ${response}=    Publish Release    ${version}    ${model}
    Response Status Should Be    201    ${response}
    VAR    ${RELEASE_ID}    ${response.json()}[id]    scope=TEST
    VAR    ${RELEASE_VERSION}    ${version}    scope=TEST

A second release published after it
    ${version}=    Random Version
    ${model}=    Random Model
    ${response}=    Publish Release    ${version}    ${model}
    Response Status Should Be    201    ${response}
    VAR    ${SECOND_ID}    ${response.json()}[id]    scope=TEST

I publish a release of a new version for model "${model}"
    VAR    ${REQUESTED_MODEL}    ${model}    scope=TEST
    ${version}=    Random Version
    Publish Release    ${version}    ${model}

I publish the same version again
    ${model}=    Random Model
    Publish Release    ${RELEASE_VERSION}    ${model}

I publish a release with a size of ${size:\d+} bytes
    ${version}=    Random Version
    ${model}=    Random Model
    Publish Release    ${version}    ${model}    size_bytes=${{ int($size) }}

I list the releases
    List Releases

The response contains the release with an id
    ${body}=    Set Variable    ${RESPONSE.json()}
    Should Match Regexp    ${body}[id]    ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$
    Should Be Equal    ${body}[targetModel]    ${REQUESTED_MODEL}
    Should End With    ${RESPONSE.headers}[Location]    /api/v1/releases/${body}[id]

The second release is listed before the first
    ${ids}=    Evaluate    [release["id"] for release in $RESPONSE.json()]
    ${second}=    Get Index From List    ${ids}    ${SECOND_ID}
    ${first}=    Get Index From List    ${ids}    ${RELEASE_ID}
    Should Be True    ${second} >= 0 and ${first} >= 0 and ${second} < ${first}    msg=${ids}
