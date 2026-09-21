# ADR-0001: One repository for the Java and Go services

- Status: accepted
- Date: 2026-09-21

## Context

FleetOps consists of a Spring Boot API, two Go services and, later, a Python model
service, plus tests, Helm charts, Terraform and pipelines. I work alone and want a
change to code, pipeline and deployment to be reviewable as one unit. I also want the
repository to show a realistic delivery setup to someone reading it.

## Options considered

1. **One repository (monorepo)** - one history, one board, one CI configuration with
   path filters. Simple to navigate. Risk: pipelines get slow if every push builds
   everything, and the repository can become a dumping ground.
2. **One repository per service** - independent versioning and smaller pipelines,
   which is closer to how large organisations work. Cost: cross-cutting changes need
   several pull requests, and I would maintain four sets of pipeline configuration for
   a project I work on alone.

## Decision

One repository, with `services/`, `tests/`, `deploy/`, `infra/`, `platform/` and
`docs/` at the top level. CI jobs are triggered by path filters so only the changed
service is built. Each service keeps its own version tag prefix if that becomes
necessary later.

## Consequences

- A feature that touches API, chart and pipeline is one pull request.
- CI configuration must use path filters from the start, or build times will grow.
- If a service ever needs to be released on its own schedule, it can be split out;
  the Helm chart and Dockerfile already live beside it.
- Another database or service can be added without new repository setup.
