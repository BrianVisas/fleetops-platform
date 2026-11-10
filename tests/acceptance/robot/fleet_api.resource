*** Settings ***
Documentation       Keywords for talking to fleet-api and telemetry-ingest. Every request is made with
...                 expected_status=any, so the test decides what counts as success.
Library             RequestsLibrary
Library             Collections
Library             String


*** Variables ***
${FLEET_API_URL}    %{FLEET_API_URL=http://localhost:8080}
${TELEMETRY_URL}    %{TELEMETRY_URL=http://localhost:8081}
# Digits and capitals without I, O and Q: the characters a real VIN may use.
${VIN_CHARS}        ABCDEFGHJKLMNPRSTUVWXYZ0123456789


*** Keywords ***
Connect To Fleet Api
    Create Session    fleet    ${FLEET_API_URL}    headers={"Accept": "application/json"}    timeout=10

Random VIN
    ${vin}=    Generate Random String    17    ${VIN_CHARS}
    RETURN    ${vin}

Random Model
    # A model name no other test uses, so list filters see only this test's
    # vehicles even though the database is shared and never reset.
    ${suffix}=    Generate Random String    8    [UPPER][NUMBERS]
    RETURN    AT-${suffix}

Register Vehicle
    [Arguments]    ${vin}    ${model}=ID.4    ${sw_version}=1.0.0
    ${body}=    Create Dictionary    vin=${vin}    model=${model}    swVersion=${sw_version}
    ${response}=    POST On Session    fleet    /api/v1/vehicles    json=${body}    expected_status=any
    VAR    ${RESPONSE}    ${response}    scope=TEST
    RETURN    ${response}

Fetch Vehicle
    [Arguments]    ${id}
    ${response}=    GET On Session    fleet    /api/v1/vehicles/${id}    expected_status=any
    VAR    ${RESPONSE}    ${response}    scope=TEST
    RETURN    ${response}

Update Vehicle
    [Arguments]    ${id}    ${model}    ${sw_version}
    ${body}=    Create Dictionary    model=${model}    swVersion=${sw_version}
    ${response}=    PUT On Session    fleet    /api/v1/vehicles/${id}    json=${body}    expected_status=any
    VAR    ${RESPONSE}    ${response}    scope=TEST
    RETURN    ${response}

Delete Vehicle
    [Arguments]    ${id}
    ${response}=    DELETE On Session    fleet    /api/v1/vehicles/${id}    expected_status=any
    VAR    ${RESPONSE}    ${response}    scope=TEST
    RETURN    ${response}

List Vehicles
    [Arguments]    ${model}    ${page}    ${size}
    ${params}=    Create Dictionary    model=${model}    page=${page}    size=${size}
    ${response}=    GET On Session    fleet    /api/v1/vehicles    params=${params}    expected_status=any
    RETURN    ${response}

Response Status Should Be
    [Arguments]    ${expected}    ${response}=${RESPONSE}
    Should Be Equal As Integers    ${response.status_code}    ${expected}
    ...    msg=${response.status_code}: ${response.text}

Connect To Telemetry
    Create Session    telemetry    ${TELEMETRY_URL}    headers={"Accept": "application/json"}    timeout=10

Reading Body
    [Documentation]    A valid reading, recorded ${minutes_ago} minutes before now.
    [Arguments]    ${vehicle_id}    ${speed}=50.0    ${minutes_ago}=0
    ${recorded_at}=    Evaluate
    ...    (datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(minutes=int($minutes_ago))).isoformat().replace("+00:00", "Z")
    ...    modules=datetime
    ${body}=    Create Dictionary    vehicleId=${vehicle_id}    recordedAt=${recorded_at}
    ...    speedKmh=${{ float($speed) }}    batteryTemp=${25.0}    voltage=${400.0}
    RETURN    ${body}

Send Reading
    [Arguments]    ${body}
    ${response}=    POST On Session    telemetry    /telemetry    json=${body}    expected_status=any
    VAR    ${RESPONSE}    ${response}    scope=TEST
    RETURN    ${response}

Latest Reading
    [Arguments]    ${vehicle_id}
    ${response}=    GET On Session    telemetry    /vehicles/${vehicle_id}/latest    expected_status=any
    RETURN    ${response}
