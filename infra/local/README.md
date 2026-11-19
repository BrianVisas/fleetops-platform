# Local Kubernetes cluster

A one-node kind cluster on Colima, with what every environment on it shares:
Envoy Gateway as the entry point (ADR-0008) and the CloudNativePG operator for
PostgreSQL. FleetOps runs on it in two ways: installed by hand from this
checkout (`make cluster-deploy`), or run by Argo CD from git after a one-time
`make gitops-bootstrap` (ADR-0010).

| File | What it is |
|------|------------|
| `kind-config.yaml` | the cluster: one node, port 30080 mapped to `127.0.0.1:8000` |
| `versions.env` | pinned versions and checksums, read by `cluster-up.sh` and by CI |
| `envoy-gateway-values.yaml`, `cnpg-values.yaml` | values for the Envoy Gateway and CloudNativePG Helm charts, used by `cluster-up.sh` and by Argo CD |
| `gateway.yaml` | the `EnvoyProxy` settings for kind, the `GatewayClass` and the `fleetops` `Gateway` |
| `cluster-up.sh`, `cluster-down.sh` | create and delete the cluster |
| `namespaces.yaml` | `fleetops-dev`, `fleetops-staging` and `argocd`, labelled for the gateway; the FleetOps ones enforce the restricted pod security profile |
| `install-fleetops.sh` | builds this checkout's images, loads them into the node and installs FleetOps into `fleetops-dev` with Helm, on a cluster without Argo CD |
| `gitops-bootstrap.sh` | once per cluster: installs Argo CD, creates its credentials in the cluster, and hands the cluster to `deploy/gitops` |
| `.cache/` | downloads, such as the chart archives, checked against `versions.env`; not in git |

## Use

Colima's VM gets 4 CPUs and 5 GiB, once:

```bash
colima stop
colima start --cpu 4 --memory 5
```

On the 8 GiB Mac, a VM with all 8 GiB lived partly in macOS swap. It froze
whenever it touched a swapped-out page, and the cluster's control plane
restarted every time a freeze outlasted its leader-election lease (EL-028).

```bash
make down          # the Compose stack and the cluster do not both fit in 8 GiB
make cluster-up
make cluster-deploy
make cluster-status
make cluster-down
```

`make cluster-deploy` installs the database (`deploy/charts/fleetops-postgres`)
and waits until PostgreSQL answers. Then it builds the service images from this
checkout, copies them into the kind node with `kind load` (no registry, no
download), and installs fleet-api, telemetry-ingest and web-ui
(`deploy/charts/fleetops-service`). `SIM=1 make cluster-deploy` adds
vehicle-sim. It ends with the dashboard and the API answering through the
gateway: open http://dev.fleetops.localhost:8000.

To run the acceptance tests against the cluster instead of Compose, point them
at the gateway. Browsers and curl resolve `*.localhost` by themselves; Python
and Java on macOS need a hosts entry, once:

```bash
echo "127.0.0.1 dev.fleetops.localhost" | sudo tee -a /etc/hosts
FLEET_API_URL=http://dev.fleetops.localhost:8000 TELEMETRY_URL=http://dev.fleetops.localhost:8000 \
  tests/acceptance/pytest-bdd/run.sh
```

`make cluster-up` ends by sending a request to `http://127.0.0.1:8000` for
`gateway-check.fleetops.localhost`, a host no route uses, and expects Envoy to
answer `404`: the request made it from the Mac through Colima and kind to Envoy.
Run it again at any time, with or without FleetOps installed; it keeps an
existing cluster and re-applies the rest.

kind switches `kubectl` to the `kind-fleetops` context. `kubectl config
get-contexts` shows where it points.

## GitOps

After `make cluster-up`, on a cluster without a manual install:

```bash
make gitops-bootstrap
make gitops-status
```

It needs two credentials, which stay in `~/.config/fleetops` and in the
cluster and never in git:

- **a deploy key** for Argo CD to read this repository. The bootstrap creates
  it and adds it to the repository, read-only, with `gh`.
- **a GHCR token** for the cluster to pull the private images. GHCR has no
  fine-grained tokens, so it is a classic one; create it with only
  `read:packages` at
  https://github.com/settings/tokens/new?scopes=read:packages&description=fleetops-ghcr-pull
  and save it:

  ```bash
  mkdir -p ~/.config/fleetops
  (umask 077; pbpaste > ~/.config/fleetops/ghcr-read-token)
  ```

  The bootstrap refuses a token with any other scope.

It ends with every application synced and healthy, and the dashboards
answering on http://dev.fleetops.localhost:8000 and
http://staging.fleetops.localhost:8000. Argo CD's UI is on
http://argocd.fleetops.localhost:8000. From then on, a change to the cluster
is a pull request to `deploy/gitops` (see its README), and
`make cluster-deploy` refuses to run.

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
`install-fleetops.sh` on a runner, with the same versions, runs the pytest-bdd
acceptance suite against the cluster, and prints what the cluster uses.

`ci.yml` calls it on every pull request, every push to `main` and every night.
It builds each image once per commit (ADR-0009), and `install-fleetops.sh` runs
with `IMAGE_TAG=ci`, which loads those images into the node instead of
building them again.
