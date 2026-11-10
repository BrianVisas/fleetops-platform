*** Settings ***
Documentation       Steps used by more than one suite file.
Resource            fleet_api.resource


*** Keywords ***
A registered vehicle
    ${vin}=    Random VIN
    ${response}=    Register Vehicle    ${vin}
    Response Status Should Be    201    ${response}
    VAR    &{VEHICLE}    id=${response.json()}[id]    vin=${vin}    model=ID.4    swVersion=1.0.0    scope=TEST

The response status is ${status:\d+}
    Response Status Should Be    ${status}

The error message mentions "${text}"
    Should Contain    ${RESPONSE.json()}[message]    ${text}
