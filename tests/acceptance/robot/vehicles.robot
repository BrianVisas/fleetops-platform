*** Settings ***
Documentation       Customer issue #1: register and manage vehicles.
...                 As a fleet operator I want to register, list, update and delete
...                 vehicles, so that the fleet in the system matches the fleet on
...                 the road. Same scenarios as features/vehicles.feature.
Resource            common_steps.resource
Suite Setup         Connect To Fleet Api
Test Tags           issue-1


*** Test Cases ***
Registering a new vehicle
    When I register a vehicle of model "ID.4" with software version "1.0.0"
    Then the response status is 201
    And the response contains the new vehicle with an id

Registering a VIN that already exists is rejected
    Given a registered vehicle
    When I register another vehicle with the same VIN
    Then the response status is 409

Registering a vehicle with an invalid VIN is rejected
    When I register a vehicle with the VIN "TOOSHORT"
    Then the response status is 400
    And the error message mentions "vin"

Reading a stored vehicle
    Given a registered vehicle
    When I fetch that vehicle
    Then the response status is 200
    And the response shows the vehicle's VIN, model and software version

Updating a vehicle
    Given a registered vehicle
    When I update its software version to "2.0.0"
    Then the response status is 200
    And fetching the vehicle shows software version "2.0.0"

Deleting a vehicle
    Given a registered vehicle
    When I delete that vehicle
    Then the response status is 204
    And fetching the vehicle returns status 404

Operations on a vehicle that does not exist
    [Template]    Operation on a missing vehicle returns 404
    fetch
    update
    delete

Listing vehicles of one model, page by page
    Given 3 registered vehicles of one model and 2 of another
    When I list vehicles of the first model, 2 per page
    Then page 1 holds 2 vehicles, all of the first model
    And page 2 holds 1 vehicle, all of the first model


*** Keywords ***
${first:\d+} registered vehicles of one model and ${second:\d+} of another
    ${first_model}=    Random Model
    ${other_model}=    Random Model
    VAR    ${FIRST_MODEL}    ${first_model}    scope=TEST
    FOR    ${i}    IN RANGE    ${first}
        ${vin}=    Random VIN
        ${response}=    Register Vehicle    ${vin}    model=${first_model}
        Response Status Should Be    201    ${response}
    END
    FOR    ${i}    IN RANGE    ${second}
        ${vin}=    Random VIN
        ${response}=    Register Vehicle    ${vin}    model=${other_model}
        Response Status Should Be    201    ${response}
    END

I register a vehicle of model "${model}" with software version "${sw_version}"
    ${vin}=    Random VIN
    VAR    &{VEHICLE}    vin=${vin}    model=${model}    swVersion=${sw_version}    scope=TEST
    Register Vehicle    ${vin}    model=${model}    sw_version=${sw_version}

I register another vehicle with the same VIN
    Register Vehicle    ${VEHICLE}[vin]    model=ID.3

I register a vehicle with the VIN "${vin}"
    Register Vehicle    ${vin}

I fetch that vehicle
    Fetch Vehicle    ${VEHICLE}[id]

I update its software version to "${sw_version}"
    Update Vehicle    ${VEHICLE}[id]    ${VEHICLE}[model]    ${sw_version}

I delete that vehicle
    Delete Vehicle    ${VEHICLE}[id]

I list vehicles of the first model, ${size:\d+} per page
    VAR    ${PAGE_SIZE}    ${size}    scope=TEST

Operation on a missing vehicle returns 404
    [Arguments]    ${operation}
    ${missing}=    Evaluate    str(uuid.uuid4())    modules=uuid
    IF    $operation == 'fetch'
        Fetch Vehicle    ${missing}
    ELSE IF    $operation == 'update'
        Update Vehicle    ${missing}    ID.4    1.0.0
    ELSE IF    $operation == 'delete'
        Delete Vehicle    ${missing}
    ELSE
        Fail    unknown operation: ${operation}
    END
    Response Status Should Be    404

The response contains the new vehicle with an id
    ${body}=    Set Variable    ${RESPONSE.json()}
    Should Match Regexp    ${body}[id]    ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$
    Should Be Equal    ${body}[vin]    ${VEHICLE}[vin]
    Should End With    ${RESPONSE.headers}[Location]    /api/v1/vehicles/${body}[id]

The response shows the vehicle's VIN, model and software version
    ${body}=    Set Variable    ${RESPONSE.json()}
    Should Be Equal    ${body}[vin]    ${VEHICLE}[vin]
    Should Be Equal    ${body}[model]    ${VEHICLE}[model]
    Should Be Equal    ${body}[swVersion]    ${VEHICLE}[swVersion]

Fetching the vehicle shows software version "${sw_version}"
    ${response}=    Fetch Vehicle    ${VEHICLE}[id]
    Response Status Should Be    200    ${response}
    Should Be Equal    ${response.json()}[swVersion]    ${sw_version}

Fetching the vehicle returns status ${status:\d+}
    ${response}=    Fetch Vehicle    ${VEHICLE}[id]
    Response Status Should Be    ${status}    ${response}

Page ${page:\d+} holds ${count:\d+} ${noun:vehicles?}, all of the first model
    ${response}=    List Vehicles    ${FIRST_MODEL}    ${{ int($page) - 1 }}    ${PAGE_SIZE}
    Response Status Should Be    200    ${response}
    ${models}=    Evaluate    [vehicle["model"] for vehicle in $response.json()["content"]]
    ${expected}=    Evaluate    [$FIRST_MODEL] * int($count)
    Lists Should Be Equal    ${models}    ${expected}
