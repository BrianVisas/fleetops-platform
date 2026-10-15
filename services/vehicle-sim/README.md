# vehicle-sim

Simulates a fleet of vehicles: registers them, sends telemetry, polls for OTA
updates, installs them and reports the outcome. One goroutine per vehicle, Go
standard library only.

It is both the client that exercises the system end to end and, from Phase 5, the
load generator next to k6.

## Run it

```bash
go test ./...
go run . -vehicles 50 -rate 1
```

With fleet-api on 8080 and telemetry-ingest on 8081 running. Ctrl-C stops it and
prints a summary.

## Flags

| Flag | Default | Meaning |
|------|---------|---------|
| `-vehicles` | `10` | how many vehicles to simulate |
| `-rate` | `1` | telemetry messages per vehicle per second |
| `-api` | `http://localhost:8080` | fleet-api base url |
| `-ingest` | `http://localhost:8081` | telemetry-ingest base url |
| `-model` | `ID.4` | model to register |
| `-sw-version` | `1.0.0` | version the fleet starts on |
| `-vin-prefix` | `SMX` | 3 characters, no I, O or Q |
| `-fail-rate` | `0` | share of installations that report failure (0 to 1) |
| `-poll-interval` | `10s` | how often a vehicle asks for an update |
| `-install-min` / `-install-max` | `2s` / `5s` | simulated install time |
| `-duration` | `0` | stop after this long; 0 means run until interrupted |
| `-timeout` | `5s` | per-request timeout |
| `-verbose` | `false` | log every rejected request |

## Useful invocations

```bash
# a fleet to click around with
go run . -vehicles 20

# feed the auto-pause requirement: half the installations fail
go run . -vehicles 20 -fail-rate 0.5 -poll-interval 2s

# the Phase 5 load target, ten minutes
go run . -vehicles 500 -rate 1 -duration 10m

# quick smoke run
go run . -vehicles 5 -rate 2 -duration 10s -poll-interval 2s
```

## Output

```
msg="simulation finished" elapsed=4s summary="vehicles=5 telemetry_sent=35
telemetry_failed=0 updates_received=5 updates_succeeded=2 updates_failed=3
polls_failed=0 reports_failed=0" loss_rate=0.000%
```

`loss_rate` is the share of telemetry the server did not accept - the number
requirement 7 puts a 1% ceiling on.

## Notes

- Every run generates a random 6-character run tag, so VINs never collide with an
  earlier run and registration never hits a 409.
- Each vehicle has its own random source; the global `math/rand` source would
  serialise 500 goroutines on one mutex.
- Vehicles start at a random offset inside the telemetry interval, so a 500-vehicle
  fleet spreads its requests instead of firing them in lockstep.
- Registration happens up front, 8 at a time, and the run aborts if it fails -
  a half-registered fleet would report misleading numbers.
