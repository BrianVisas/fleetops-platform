# Local Kubernetes cluster

A one-node kind cluster on Colima, with what every environment on it shares:
Envoy Gateway as the entry point (ADR-0008) and the CloudNativePG operator for
PostgreSQL. Phase 4 builds on it: Helm charts, then Argo CD.

| File | What it is |
|------|------------|
| `kind-config.yaml` | the cluster: one node, port 30080 mapped to `127.0.0.1:8000` |
| `versions.env` | pinned versions and checksums, read by `cluster-up.sh` and by CI |
| `envoy-gateway-values.yaml` | values for the Envoy Gateway Helm chart |
| `gateway.yaml` | the `EnvoyProxy` settings for kind, the `GatewayClass` and the `fleetops` `Gateway` |
| `cluster-up.sh`, `cluster-down.sh` | create and delete the cluster |
| `namespaces.yaml` | the FleetOps environments, labelled for the gateway and the restricted pod security profile |
| `install-fleetops.sh` | installs FleetOps into `fleetops-dev` with Helm, until Argo CD does it |
| `.cache/` | downloads, such as the CloudNativePG manifest, checked against `versions.env`; not in git |

## Use

```bash
make down          # the Compose stack and the cluster do not both fit in 8 GiB
make cluster-up
make cluster-deploy
make cluster-status
make cluster-down
```

`make cluster-deploy` installs the database (`deploy/charts/fleetops-postgres`)
and waits until PostgreSQL answers.

`make cluster-up` ends by sending a request to `http://127.0.0.1:8000` and
expects Envoy to answer `404`: nothing is routed yet, but the request made it
from the Mac through Colima and kind to Envoy. Run it again at any time; it
keeps an existing cluster and re-applies the rest.

kind switches `kubectl` to the `kind-fleetops` context. `kubectl config
get-contexts` shows where it points.

## Ports

| Where | Port |
|-------|------|
| Envoy listener, inside the cluster | 80 |
| Envoy service (NodePort) | 30080 |
| The Mac | `127.0.0.1:8000` |

Routes use one hostname per environment: `dev.fleetops.localhost` and
`staging.fleetops.localhost`. Browsers and curl resolve `*.localhost` to
`127.0.0.1` by themselves.

## CI

`.github/workflows/cluster.yml` lints the charts, then runs `cluster-up.sh` and
`install-fleetops.sh` on a runner whenever `infra/local` or `deploy/charts`
changes, with the same versions, and prints what the cluster uses.
