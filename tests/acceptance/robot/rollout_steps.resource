*** Settings ***
Documentation       Steps shared by rollouts.robot (#4) and updates.robot (#5).
...                 Vehicles are numbered from 1 in the order a test registered them.
Resource            common_steps.resource


*** Keywords ***
${count:\d+} ${noun:vehicles?} of a new model on software version "${version}"
    ${model}=    Random Model
    VAR    ${MODEL}    ${model}    scope=TEST
    VAR    @{VEHICLES}    scope=TEST
    FOR    ${i}    IN RANGE    ${count}
        ${vin}=    Random VIN
        ${response}=    Register Vehicle    ${vin}    model=${model}    sw_version=${version}
        Response Status Should Be    201    ${response}
        Append To List    ${VEHICLES}    ${response.json()}[id]
    END

A release of a new version for that model
    ${version}=    Random Version
    ${response}=    Publish Release    ${version}    ${MODEL}
    Response Status Should Be    201    ${response}
    ${body}=    Set Variable    ${response.json()}
    VAR    &{RELEASE}    &{body}    scope=TEST

${count:\d+} ${noun:vehicles?} of that model already on the release version
    FOR    ${i}    IN RANGE    ${count}
        ${vin}=    Random VIN
        ${response}=    Register Vehicle    ${vin}    model=${MODEL}    sw_version=${RELEASE}[version]
        Response Status Should Be    201    ${response}
    END

${count:\d+} ${noun:vehicles?} of another model
    ${model}=    Random Model
    FOR    ${i}    IN RANGE    ${count}
        ${vin}=    Random VIN
        ${response}=    Register Vehicle    ${vin}    model=${model}
        Response Status Should Be    201    ${response}
    END

A rollout of the release to ${percentage:\d+}%
    ${response}=    Create Rollout    ${RELEASE}[id]    ${percentage}
    Response Status Should Be    201    ${response}
    VAR    ${ROLLOUT_ID}    ${response.json()}[id]    scope=TEST

The rollout is paused
    ${response}=    Pause Rollout    ${ROLLOUT_ID}
    Response Status Should Be    200    ${response}

Every vehicle of that model has asked for its update
    FOR    ${vehicle_id}    IN    @{VEHICLES}
        ${response}=    Claim Update    ${vehicle_id}
        Should Contain    ${{ [200, 204] }}    ${response.status_code}
    END

Vehicle ${number:\d+} has asked for its update
    ${response}=    Claim Update    ${VEHICLES}[${{ int($number) - 1 }}]
    Response Status Should Be    200    ${response}

Vehicle ${number:\d+} has installed the update successfully
    Vehicle ${number} has asked for its update
    ${response}=    Report Update Result    ${VEHICLES}[${{ int($number) - 1 }}]    ${RELEASE}[version]    ${True}
    Response Status Should Be    202    ${response}

Vehicle ${number:\d+} has failed to install the update
    Vehicle ${number} has asked for its update
    ${response}=    Report Update Result    ${VEHICLES}[${{ int($number) - 1 }}]    ${RELEASE}[version]    ${False}
    Response Status Should Be    202    ${response}

I create a rollout of the release to ${percentage:\d+}%
    ${response}=    Create Rollout    ${RELEASE}[id]    ${percentage}
    IF    ${response.status_code} == 201
        VAR    ${ROLLOUT_ID}    ${response.json()}[id]    scope=TEST
    END

I raise the rollout to ${percentage:\d+}%
    Change Rollout Percentage    ${ROLLOUT_ID}    ${percentage}

I pause the rollout
    Pause Rollout    ${ROLLOUT_ID}

I look at the rollout
    ${response}=    Get Rollout    ${ROLLOUT_ID}
    VAR    ${RESPONSE}    ${response}    scope=TEST

Vehicle ${number:\d+} asks for its update
    Claim Update    ${VEHICLES}[${{ int($number) - 1 }}]

Vehicle ${number:\d+} reports that the installation ${outcome:succeeded|failed}
    Report Update Result    ${VEHICLES}[${{ int($number) - 1 }}]    ${RELEASE}[version]    ${{ $outcome == 'succeeded' }}

The rollout targets ${count:\d+} ${noun:vehicles?}
    ${response}=    Get Rollout    ${ROLLOUT_ID}
    Response Status Should Be    200    ${response}
    ${body}=    Set Variable    ${response.json()}
    ${total}=    Evaluate    $body["pending"] + $body["installing"] + $body["succeeded"] + $body["failed"]
    Should Be Equal As Integers    ${total}    ${count}    msg=${response.text}

The rollout shows ${pending:\d+} pending, ${installing:\d+} installing, ${succeeded:\d+} succeeded and ${failed:\d+} failed
    ${response}=    Get Rollout    ${ROLLOUT_ID}
    Response Status Should Be    200    ${response}
    ${body}=    Set Variable    ${response.json()}
    ${actual}=    Evaluate    [$body["pending"], $body["installing"], $body["succeeded"], $body["failed"]]
    ${expected}=    Evaluate    [int($pending), int($installing), int($succeeded), int($failed)]
    Lists Should Be Equal    ${actual}    ${expected}    msg=${response.text}

The rollout status is ${status}
    ${response}=    Get Rollout    ${ROLLOUT_ID}
    Should Be Equal    ${response.json()}[status]    ${status}

The response offers the release
    ${body}=    Set Variable    ${RESPONSE.json()}
    Should Be Equal    ${body}[version]    ${RELEASE}[version]
    Should Be Equal    ${body}[checksum]    ${RELEASE}[checksum]
    Should Be Equal As Integers    ${body}[sizeBytes]    ${RELEASE}[sizeBytes]

Vehicle ${number:\d+} is on the release version
    ${response}=    Fetch Vehicle    ${VEHICLES}[${{ int($number) - 1 }}]
    Should Be Equal    ${response.json()}[swVersion]    ${RELEASE}[version]

Vehicle ${number:\d+} is still on software version "${version}"
    ${response}=    Fetch Vehicle    ${VEHICLES}[${{ int($number) - 1 }}]
    Should Be Equal    ${response.json()}[swVersion]    ${version}
