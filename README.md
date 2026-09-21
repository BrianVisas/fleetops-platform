# FleetOps Platform

A practice project for DevOps, test automation, cloud and platform engineering.

FleetOps is a small vehicle-fleet platform: register vehicles, ingest their telemetry,
and roll out software releases over the air (OTA). I build it, test it, deploy it and
accept it as the customer.

## Services

| Service            | Language            | Status        |
|--------------------|---------------------|---------------|
| `fleet-api`        | Java 21, Spring Boot| Phase 1       |
| `telemetry-ingest` | Go                  | Phase 1       |
| `vehicle-sim`      | Go                  | Phase 1       |
| `web-ui`           | HTML/JS or React    | Phase 3       |
| `anomaly-model`    | Python, FastAPI     | Phase 9 (ML)  |

## Repository layout

```
services/   application code, one folder per service
tests/      api, e2e, load and contract tests
deploy/     docker-compose, Helm charts, GitOps (Argo CD)
infra/      Terraform for local, Azure and STACKIT
platform/   Backstage catalog, templates, policies
docs/       ADRs, runbooks, test strategy, release reports
```

## Environments

| Name            | Where                 | Purpose                      |
|-----------------|-----------------------|------------------------------|
| `local`         | Docker Compose / kind | daily development            |
| `dev`           | kind + Argo CD        | GitOps delivery check        |
| `azure-staging` | AKS                   | cloud release rehearsal      |
| `stackit-prod`  | STACKIT SKE           | customer production release  |

## Getting started

Nothing to run yet. Start with Phase 0 in the roadmap and `docs/WORKFLOW.md`.

## Progress

- [ ] Phase 0 - Foundations
- [ ] Phase 1 - Services, unit tests, Docker (v0.1.0)
- [ ] Phase 2 - CI pipeline (v0.2.0)
- [ ] Phase 3 - Test automation (v0.3.0)
- [ ] Phase 4 - Kubernetes, Helm, GitOps (v0.4.0)
- [ ] Phase 5 - Contract and performance tests (v0.5.0)
- [ ] Phase 6 - Terraform on Azure and STACKIT (v0.6.0)
- [ ] Phase 7 - Observability and chaos (v0.7.0)
- [ ] Phase 8 - Platform engineering (v1.0.0)
- [ ] Phase 9 - MLOps (v1.1.0)
- [ ] Phase 10 - Delivery and portfolio (v1.2.0)
