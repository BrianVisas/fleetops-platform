#!/usr/bin/env bash
# Creates the labels and the 7 customer requirement issues.
# Run once, from the repo root, after `gh auth login`.
set -euo pipefail

echo "Creating labels..."
gh label create customer   --color 0E8A16 --description "Written by the customer role"   --force
gh label create feature    --color 1D76DB --description "New capability"                 --force
gh label create bug        --color D73A4A --description "Does not meet acceptance criteria" --force
gh label create tech-debt  --color FBCA04 --description "Cleanup or refactoring"          --force
for n in 0 1 2 3 4 5 6 7 8 9 10; do
  gh label create "phase-$n" --color BFD4F2 --description "Roadmap phase $n" --force
done

new_issue () {
  gh issue create --title "$1" --label customer,feature --label "$2" --body "$3"
}

echo "Creating requirement issues..."

new_issue "Register and manage vehicles" phase-1 '## User story

As a fleet operator, I want to register, list, update and delete vehicles, so that the
fleet in the system matches the fleet on the road.

## Acceptance criteria

- [ ] Given a VIN, model and software version, when I POST /api/v1/vehicles, then the
      vehicle is stored and returned with an id and a 201 status.
- [ ] Given a VIN that already exists, when I register it again, then I get 409.
- [ ] Given a stored vehicle, when I GET /api/v1/vehicles/{id}, then I get its data.
- [ ] Given a missing id, when I GET, PUT or DELETE it, then I get 404.
- [ ] Given vehicles exist, when I GET /api/v1/vehicles?model=X, then only that model
      is returned, paginated.'

new_issue "Ingest vehicle telemetry" phase-1 '## User story

As a fleet operator, I want vehicles to send telemetry continuously, so that I can see
the current state of each vehicle.

## Acceptance criteria

- [ ] Given a registered vehicle, when it POSTs telemetry, then the reading is stored
      and 202 is returned.
- [ ] Given an unknown vehicle id, when telemetry is posted, then 400 is returned and
      nothing is stored.
- [ ] Given a missing or non-numeric field, when telemetry is posted, then 400 is
      returned with a message naming the field.
- [ ] Given stored readings, when I GET /vehicles/{id}/latest, then the newest reading
      is returned.
- [ ] Given no readings for a vehicle, when I GET latest, then 404 is returned.'

new_issue "Publish a software release" phase-1 '## User story

As a fleet operator, I want to publish a software release, so that it can be rolled out
to vehicles.

## Acceptance criteria

- [ ] Given a version, target model, checksum and size, when I POST /api/v1/releases,
      then the release is stored and returned with 201.
- [ ] Given a version that already exists, when I publish it again, then 409.
- [ ] Given releases exist, when I GET /api/v1/releases, then they are listed newest
      first.'

new_issue "Start and pause a staged rollout" phase-1 '## User story

As a fleet operator, I want to roll a release out to a share of the fleet and pause it,
so that a bad release does not reach every vehicle.

## Acceptance criteria

- [ ] Given a release and a percentage, when I create a rollout, then only vehicles of
      the matching model whose version differs are targeted.
- [ ] Given 10 matching vehicles and 10%, when I create the rollout, then 1 vehicle is
      targeted (rounded up).
- [ ] Given a running rollout, when I raise the percentage, then only new vehicles are
      added; existing targets keep their state.
- [ ] Given a running rollout, when I pause it, then its status is PAUSED.
- [ ] Given a rollout id, when I GET it, then I see counts per state (pending,
      installing, succeeded, failed).'

new_issue "Vehicles poll for updates and report the result" phase-1 '## User story

As a vehicle, I want to ask whether an update is assigned to me and report how the
installation went, so that the fleet operator sees real progress.

## Acceptance criteria

- [ ] Given a vehicle targeted by a running rollout, when it GETs its update, then the
      release details are returned with 200.
- [ ] Given no assigned update, when it GETs its update, then 204 is returned.
- [ ] Given the rollout is paused, when a targeted vehicle GETs its update, then 204.
- [ ] Given an update was installed, when the vehicle reports success, then its stored
      software version changes and the target state becomes SUCCEEDED.
- [ ] Given the vehicle reports failure, then the state becomes FAILED and the version
      does not change.'

new_issue "Auto-pause a rollout when failures pass a threshold" phase-3 '## User story

As a fleet operator, I want a rollout to pause itself when too many installations fail,
so that a bad release stops spreading without me watching it.

## Acceptance criteria

- [ ] Given a configurable threshold (default 20%), when the failure share among
      finished targets passes it, then the rollout status becomes PAUSED automatically.
- [ ] Given the rollout auto-paused, then vehicles polling for updates get 204.
- [ ] Given fewer than 5 finished targets, then the threshold is not applied yet.
- [ ] The pause is visible in the rollout response with a reason.'

new_issue "Handle 500 vehicles at one message per second" phase-5 '## User story

As a fleet operator, I want the system to keep up with the whole fleet reporting at
once, so that telemetry is not lost during a rollout.

## Acceptance criteria

- [ ] Given 500 simulated vehicles sending 1 message per second for 10 minutes, then
      p95 ingest latency stays below 200 ms.
- [ ] Given the same load, then the error rate stays below 1%.
- [ ] Given the same load, then no telemetry rows are lost (count sent == count stored).
- [ ] The test runs from a k6 script that can be executed in CI.'

echo
echo "Done. Verify with: gh issue list --label customer"
