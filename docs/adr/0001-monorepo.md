# ADR-0001: One repository for all services and infrastructure

- Status: accepted
- Date: 2026-09-17 (recorded 2026-11-09; written in Phase 0 but never committed)

## Context

FleetOps consists of three services in two languages (`fleet-api` in Java,
`telemetry-ingest` and `vehicle-sim` in Go), a shared PostgreSQL schema owned by
`fleet-api`, and, from Phase 4 on, deployment and infrastructure code (Helm charts,
Argo CD applications, Terraform). One developer works on all of it, and a typical
change touches more than one of those parts: a migration in `fleet-api` changes
what `telemetry-ingest` reads (ADR-0004).

## Options considered

1. **One repository per service, plus one for infrastructure.** Clear ownership
   and independent histories. But a cross-cutting change becomes several pull
   requests that must land in the right order, the shared schema has no single
   place where both its owner and its reader are tested, and CI configuration
   gets duplicated four times.
2. **One repository for everything (monorepo).** One pull request per logical
   change, one place for CI conventions, and the integration test for
   `telemetry-ingest` can apply `fleet-api`'s own migrations directly from the same
   checkout. The cost is that CI has to be selective: a change to one service must
   not rebuild and test the others.

## Decision

One repository, `fleetops-platform`, laid out by concern:

- `services/<name>/`: one directory per service, each with its own build
  (`pom.xml` or `go.mod`) and Dockerfile
- `deploy/`: Compose now; Helm and Argo CD from Phase 4
- `docs/`: ADRs, the security policy and the engineering log
- `scripts/`: local helpers that mirror what CI runs, such as the security scan

CI keeps the cost of the monorepo down with one workflow per service and path
filters, so a Go-only change does not run the Java build.

## Consequences

- Cross-service changes, such as a schema migration and the integration test that
  depends on it, land in a single pull request and are tested together.
- Path filters are now part of correctness, not just speed: a filter that misses
  a dependency (for example `go-services.yml` must also watch `fleet-api`'s
  migrations) means a change is not tested at all.
- Releases are versioned for the platform as a whole (ADR-0005), which matches
  how the services are actually shipped.
- Workflows with path filters do not report on pull requests they do not match,
  which has to be designed around when status checks become required.
