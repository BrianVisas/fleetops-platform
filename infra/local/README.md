# Local Kubernetes cluster

A one-node kind cluster on Colima, with Envoy Gateway as its entry point
(ADR-0008). Phase 4 builds on it: Helm charts, CloudNativePG and Argo CD.

| File | What it is |
|------|------------|
| `kind-config.yaml` | the cluster: one node, port 30080 mapped to `127.0.0.1:8000` |
| `versions.env` | pinned versions and checksums, read by `cluster-up.sh` and by CI |
| `envoy-gateway-values.yaml` | values for the Envoy Gateway Helm chart |
| `gateway.yaml` | the `EnvoyProxy` settings for kind, the `GatewayClass` and the `fleetops` `Gateway` |
| `cluster-up.sh`, `cluster-down.sh` | create and delete the cluster |

## Use

```bash
make down          # the Compose stack and the cluster do not both fit in 8 GiB
make cluster-up
make cluster-status
make cluster-down
```

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

`.github/workflows/cluster.yml` runs `cluster-up.sh` on a runner whenever
`infra/local` changes, with the same versions, and prints what the cluster
uses.
