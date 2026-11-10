*** Settings ***
Documentation       Customer issue #2: ingest vehicle telemetry.
...                 As a fleet operator I want vehicles to send telemetry
...                 continuously, so that I can see the current state of each
...                 vehicle. Same scenarios as features/telemetry.feature.
Resource            common_steps.resource
Suite Setup         Run Keywords    Connect To Fleet Api    AND    Connect To Telemetry
Test Tags           issue-2


*** Test Cases ***
A registered vehicle sends a reading
    Given a registered vehicle
    When the vehicle sends a reading with speed 88.5 km/h
    Then the response status is 202
    And the vehicle's latest reading shows speed 88.5 km/h

A reading from an unknown vehicle is rejected and not stored
    When a vehicle that is not registered sends a reading
    Then the response status is 400
    And the error message mentions "vehicleId"
    And that vehicle has no latest reading

A reading with a missing or non-numeric field is rejected
    [Template]    A reading where the field is wrong is rejected
    speedKmh       missing
    batteryTemp    missing
    voltage        non-numeric

The latest reading is the newest one, not the last one sent
    Given a registered vehicle
    And the vehicle sent a reading 2 minutes ago with speed 50 km/h
    And the vehicle sent a reading 5 minutes ago with speed 30 km/h
    When I ask for the vehicle's latest reading
    Then the response status is 200
    And the reading shows speed 50 km/h

A vehicle with no readings has no latest reading
    Given a registered vehicle
    When I ask for the vehicle's latest reading
    Then the response status is 404


*** Keywords ***
A reading where the field is wrong is rejected
    [Arguments]    ${field}    ${problem}
    A registered vehicle
    The vehicle sends a reading where ${field} is ${problem}
    The response status is 400
    The error message mentions "${field}"

The vehicle sent a reading ${minutes:\d+} minutes ago with speed ${speed} km/h
    ${body}=    Reading Body    ${VEHICLE}[id]    speed=${speed}    minutes_ago=${minutes}
    ${response}=    Send Reading    ${body}
    Response Status Should Be    202    ${response}

The vehicle sends a reading with speed ${speed} km/h
    ${body}=    Reading Body    ${VEHICLE}[id]    speed=${speed}
    Send Reading    ${body}

A vehicle that is not registered sends a reading
    ${unknown}=    Evaluate    str(uuid.uuid4())    modules=uuid
    VAR    ${UNKNOWN_ID}    ${unknown}    scope=TEST
    ${body}=    Reading Body    ${unknown}
    Send Reading    ${body}

The vehicle sends a reading where ${field} is ${problem}
    ${body}=    Reading Body    ${VEHICLE}[id]
    IF    $problem == 'missing'
        Remove From Dictionary    ${body}    ${field}
    ELSE IF    $problem == 'non-numeric'
        Set To Dictionary    ${body}    ${field}=not-a-number
    ELSE
        Fail    unknown problem: ${problem}
    END
    Send Reading    ${body}

I ask for the vehicle's latest reading
    ${response}=    Latest Reading    ${VEHICLE}[id]
    VAR    ${RESPONSE}    ${response}    scope=TEST

The vehicle's latest reading shows speed ${speed} km/h
    ${response}=    Latest Reading    ${VEHICLE}[id]
    Response Status Should Be    200    ${response}
    Should Be Equal As Numbers    ${response.json()}[speedKmh]    ${speed}

That vehicle has no latest reading
    ${response}=    Latest Reading    ${UNKNOWN_ID}
    Response Status Should Be    404    ${response}

The reading shows speed ${speed} km/h
    Should Be Equal As Numbers    ${RESPONSE.json()}[speedKmh]    ${speed}
