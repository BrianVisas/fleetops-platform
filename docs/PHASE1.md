# Phase 1 — Build the services

Goal: `make up` starts Postgres, `fleet-api`, `telemetry-ingest` and `vehicle-sim`,
and the simulator's telemetry shows up through the API. Unit tests pass. Release `v0.1.0`.

Nothing in this phase touches Kubernetes, CI or the cloud. Keep it simple; those come later.

## Work packages

Do them in this order, one branch and one pull request each.

| # | Branch | What it delivers |
|---|--------------------------------|-----------------------------------------------|
| 1 | `feat/1-fleet-api-skeleton`    | Spring Boot project, health endpoint, Postgres, Flyway schema |
| 2 | `feat/2-vehicles-api`          | Vehicle CRUD + unit tests (requirement 1)     |
| 3 | `feat/3-releases-rollouts-api` | Releases, rollouts, update polling (requirements 3, 4, 5) |
| 4 | `feat/4-telemetry-ingest`      | Go ingest service + unit tests (requirement 2)|
| 5 | `feat/5-vehicle-sim`           | Go simulator CLI                              |
| 6 | `feat/6-compose-and-docker`    | Dockerfiles, docker-compose, `make up`        |

Requirement 6 (auto-pause on failures) and requirement 7 (load) come in Phases 3 and 5.

## Data model (Flyway `V1__init.sql` in `fleet-api`)

```sql
create table vehicle (
    id              uuid primary key,
    vin             varchar(17) not null unique,
    model           varchar(64) not null,
    sw_version      varchar(32) not null,
    registered_at   timestamptz not null default now()
);

create table software_release (
    id           uuid primary key,
    version      varchar(32) not null unique,
    target_model varchar(64) not null,
    checksum     varchar(64) not null,
    size_bytes   bigint      not null,
    published_at timestamptz not null default now()
);

create table rollout (
    id          uuid primary key,
    release_id  uuid not null references software_release(id),
    status      varchar(16) not null,   -- CREATED, RUNNING, PAUSED, COMPLETED
    percentage  int  not null,          -- 10, 50, 100
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create table rollout_target (
    rollout_id  uuid not null references rollout(id),
    vehicle_id  uuid not null references vehicle(id),
    state       varchar(16) not null,   -- PENDING, INSTALLING, SUCCEEDED, FAILED
    updated_at  timestamptz not null default now(),
    primary key (rollout_id, vehicle_id)
);

create table telemetry (
    id           bigserial primary key,
    vehicle_id   uuid not null references vehicle(id),
    recorded_at  timestamptz not null,
    speed_kmh    real not null,
    battery_temp real not null,
    voltage      real not null
);

create index telemetry_vehicle_time on telemetry (vehicle_id, recorded_at desc);
```

`telemetry-ingest` writes to the `telemetry` table and reads `vehicle` to validate ids.
It never writes to the other tables. Same database, separate concerns — a deliberate
trade-off worth an ADR.

## fleet-api endpoints (Java, port 8080)

| Method | Path | Body / params | Returns |
|--------|------|---------------|---------|
| GET | `/actuator/health` | — | 200 |
| POST | `/api/v1/vehicles` | `{vin, model, swVersion}` | 201 + vehicle |
| GET | `/api/v1/vehicles` | `?model=&page=&size=` | 200 + page |
| GET | `/api/v1/vehicles/{id}` | — | 200 or 404 |
| PUT | `/api/v1/vehicles/{id}` | `{model, swVersion}` | 200 or 404 |
| DELETE | `/api/v1/vehicles/{id}` | — | 204 or 404 |
| POST | `/api/v1/releases` | `{version, targetModel, checksum, sizeBytes}` | 201 |
| GET | `/api/v1/releases` | — | 200 |
| POST | `/api/v1/rollouts` | `{releaseId, percentage}` | 201, picks target vehicles |
| GET | `/api/v1/rollouts/{id}` | — | 200 + progress counts |
| POST | `/api/v1/rollouts/{id}/pause` | — | 200 |
| POST | `/api/v1/rollouts/{id}/resume` | — | 200 |
| GET | `/api/v1/vehicles/{id}/update` | — | 200 + release to install, or 204 |
| POST | `/api/v1/vehicles/{id}/update/result` | `{version, success}` | 202 |

Rules worth testing:

- A VIN is unique; a duplicate returns 409.
- A rollout only targets vehicles whose `model` matches the release's `targetModel`
  and whose `swVersion` differs from the release version.
- `percentage` picks that share of matching vehicles, rounded up, and never re-picks
  a vehicle already in the rollout.
- `GET /update` returns 204 while the rollout is `PAUSED`.
- A successful result updates the vehicle's `swVersion`.

Errors use one shape: `{"timestamp", "status", "error", "message", "path"}`.

## telemetry-ingest endpoints (Go, port 8081)

| Method | Path | Body | Returns |
|--------|------|------|---------|
| GET | `/healthz` | — | 200 |
| POST | `/telemetry` | `{vehicleId, recordedAt, speedKmh, batteryTemp, voltage}` | 202 |
| GET | `/vehicles/{id}/latest` | — | 200 + newest row, or 404 |

Validation: unknown `vehicleId` → 400; missing field → 400; `recordedAt` more than
five minutes in the future → 400.

## vehicle-sim (Go CLI)

```
vehicle-sim --vehicles 50 --rate 1 --api http://localhost:8080 --ingest http://localhost:8081
```

- Registers N vehicles on start (or reuses them if the VINs already exist).
- Each vehicle sends telemetry `--rate` times per second with plausible values.
- Every 10 seconds each vehicle polls `GET /update`; if it gets a release it waits
  2–5 seconds, then reports success, or failure for a configurable share of vehicles
  (`--fail-rate 0.1`). That failure share is what Phase 3 uses to test auto-pause.

## Tests to write in this phase

- **fleet-api:** service-layer unit tests with Mockito for rollout target selection,
  percentage rounding, pause behaviour and VIN conflicts. Web-layer tests with
  `@WebMvcTest` for status codes and validation. Aim for 70% line coverage.
- **telemetry-ingest:** table-driven tests for validation, handler tests with
  `httptest`, and one test per error path.
- Database integration tests wait for Phase 2 (Testcontainers).

## Done when

- [ ] `make up` brings the whole stack up from a clean machine
- [ ] `vehicle-sim --vehicles 50` runs and `GET /vehicles/{id}/latest` returns fresh data
- [ ] A release + rollout at 10% assigns updates to the right vehicles and their
      `swVersion` changes after they report success
- [ ] `make test` runs all unit tests green
- [ ] README updated with how to run it
- [ ] Tag `v0.1.0`
