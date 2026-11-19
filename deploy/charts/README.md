# Helm charts

What runs in each FleetOps environment. Phase 4 installs them on the local kind
cluster (`infra/local`); Argo CD takes over the installing in package 4.

| Chart | What it installs |
|-------|------------------|
| `fleetops-postgres` | the database: a CloudNativePG `Cluster` with PostgreSQL 17 |
| `fleetops-service` | one service, as set by its values file: `values-fleet-api.yaml`, `values-telemetry-ingest.yaml`, `values-web-ui.yaml`, `values-vehicle-sim.yaml` |

The CloudNativePG operator and Envoy Gateway are part of the cluster itself:
`infra/local/cluster-up.sh` installs them.

## One chart for four services

The four services need the same things: a Deployment that runs a non-root
image with a read-only filesystem, probes, resources, a Service, and a route
from the gateway. Four charts would be four copies of the same templates to
keep in step. So there is one chart, and each service is a values file:

| Values | fleet-api | telemetry-ingest | web-ui | vehicle-sim |
|--------|-----------|------------------|--------|-------------|
| port | 8080 | 8081 | 8080 | none (a client) |
| configuration | `DB_URL`, `JAVA_OPTS` | `PORT`, `DB_MAX_CONNS` | none | `args` |
| from the database secret | `DB_USER`, `DB_PASSWORD` | `DB_URL` (the `uri`) | | |
| probes | Spring Boot's `/actuator/health/liveness` and `/readiness`, and a startup probe | `/healthz` | `/healthz` | none |
| route paths | `/api` | `/telemetry`, `/vehicles` | `/` | none |
| memory request / limit | 512 Mi / 512 Mi | 32 Mi / 128 Mi | 16 Mi / 64 Mi | 16 Mi / 64 Mi |

An environment adds its own values on top: the hostname (`route.host`), the
image tag, and the pull secret for the private GHCR images (`imagePullSecrets`).
Argo CD's environments keep them in `deploy/gitops/envs/<env>/values.yaml`.

Every pod runs as uid 10001 with no privilege escalation, no Linux capabilities,
a read-only root filesystem with `/tmp` as the one writable place, and no
Kubernetes API token. The FleetOps namespaces enforce the restricted pod
security profile, so a pod without these settings is refused.

The HPA template is there but off: kind has no metrics-server, so an HPA would
have nothing to act on. Cloud environments (Phase 6) turn it on.

## The routes

All services share the environment's hostname, `dev.fleetops.localhost`. The
gateway sends a request to the route with the longest matching path prefix:

| Request | Goes to |
|---------|---------|
| `/api/v1/vehicles` | fleet-api |
| `/telemetry`, `/vehicles/{id}/latest` | telemetry-ingest |
| everything else, such as `/` | web-ui (which also proxies `/api` to fleet-api for the browser) |

## The database

CloudNativePG creates, for a cluster named `fleetops-db`:

| Name | What it is |
|------|------------|
| service `fleetops-db-rw` | the primary, for reads and writes |
| secret `fleetops-db-app` | the owner's credentials, generated in the cluster: `username`, `password`, `dbname`, `host`, `port`, `uri`, `jdbc-uri` |

No password is in git or in values files. The service charts read them from
that secret.

## Checking a chart

```bash
helm lint deploy/charts/fleetops-service --values deploy/charts/fleetops-service/values-fleet-api.yaml
helm template fleet-api deploy/charts/fleetops-service --values deploy/charts/fleetops-service/values-fleet-api.yaml
```

CI lints every chart with every values file, installs them all on a kind
cluster, and runs the acceptance tests against it (`.github/workflows/cluster.yml`).
