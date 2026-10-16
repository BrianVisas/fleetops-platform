# ADR-0004: telemetry-ingest shares fleet-api's database

- Status: accepted
- Date: 2026-10-15

## Context

`telemetry-ingest` has to reject readings from unknown vehicles and store accepted
ones. The vehicle registry belongs to `fleet-api`. Requirement 7 puts the target
load at 500 vehicles sending one message per second, with p95 latency under 200 ms
and no message loss.

## Options considered

1. **Call fleet-api to validate each reading.** Clean service boundary: one owner
   per table. But it adds an HTTP round trip to every one of 500 messages per
   second, makes ingest unavailable whenever fleet-api is down, and puts the
   latency target at the mercy of another service's garbage collection.
2. **Share the database, with table-level ownership.** `telemetry-ingest` reads
   `vehicle` and writes `telemetry`; `fleet-api` owns everything else and owns all
   migrations. One network hop per reading. The cost is a schema coupling that no
   compiler enforces: a column rename in `fleet-api` can break ingest silently.
3. **Give ingest its own database and replicate vehicle ids.** Proper isolation,
   but it needs a replication mechanism and an eventual-consistency story for a
   project with one developer, which is a lot of machinery for one lookup.

## Decision

Share one database with table-level ownership. `fleet-api` owns the schema and all
Flyway migrations. `telemetry-ingest` only ever reads `vehicle` and writes
`telemetry`, and runs no migrations of its own.

## Consequences

- One hop per reading, and ingest keeps working while fleet-api is down or
  redeploying - which is what a vehicle fleet needs.
- The coupling is real and undetected at compile time. Phase 2 covers it with
  integration tests against a real Postgres, and Phase 5 adds contract tests; a
  schema change that breaks ingest should fail CI, not production.
- Both services need database credentials, so Phase 6 provisions two sets of
  credentials against the same instance rather than one shared user.
- If ingest ever outgrows this, the move is its own time-series store (Postgres
  partitioning or Timescale) rather than a copy of the vehicle registry.
