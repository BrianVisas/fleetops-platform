# GitOps

What Argo CD runs on the local cluster (ADR-0010). After a one-time
`make gitops-bootstrap`, everything on the cluster comes from this directory on
`main`, and a change to it is a pull request.

| Path | What it is |
|------|------------|
| `root.yaml` | the root application, applied once by `gitops-bootstrap.sh`; it syncs `apps/` |
| `apps/projects.yaml` | the `platform` and `fleetops` AppProjects: what each kind of application may change |
| `apps/argocd.yaml` | Argo CD, managing its own release |
| `apps/envoy-gateway.yaml`, `apps/cloudnative-pg.yaml` | the platform charts, the same releases `cluster-up.sh` installs |
| `apps/platform-config.yaml` | `infra/local/gateway.yaml` and `infra/local/namespaces.yaml` |
| `apps/fleetops.yaml` | ApplicationSets: one application per environment and component |
| `argocd/values.yaml` | the Argo CD chart's values, used by the bootstrap and by `apps/argocd.yaml` |
| `envs/<env>/values.yaml` | what every service in that environment runs: image tag, hostname, pull secret. `dev` runs on the local cluster; `staging` is recorded here and runs from Phase 6 (EL-033) |
| `check-versions.sh` | CI: the platform versions here match `infra/local/versions.env` |

## Sync order

The root application applies `apps/` in waves, and each wave waits for the one
before to be healthy:

| Wave | Applications |
|------|--------------|
| -2 | the AppProjects |
| -1 | `argocd` |
| 0 | `envoy-gateway`, `cloudnative-pg` |
| 1 | `platform-config` |
| 2 | `fleetops-<env>-postgres`, `fleetops-<env>-<service>` |

## Common changes

- **Move an environment to another image:** change `image.tag` in
  `envs/<env>/values.yaml`. Every service in it moves; one commit builds all
  four images with the same tag.
- **Add a service to an environment:** an element in the `fleetops-services`
  list in `apps/fleetops.yaml`.
- **Upgrade a platform chart:** the version in `infra/local/versions.env` and in
  the application, together; `check-versions.sh` fails CI otherwise.

## Looking at it

```bash
make gitops-status
```

The UI is at http://argocd.fleetops.localhost:8000, user `admin`; the
bootstrap prints how to read the password.
