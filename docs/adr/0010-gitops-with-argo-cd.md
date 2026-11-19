# ADR-0010: Argo CD runs the cluster from git, as app-of-apps

- Status: accepted
- Date: 2026-11-19

## Context

Until Phase 4 package 4, FleetOps reached the local cluster by
`make cluster-deploy`: a script on the Mac that builds the images and runs
`helm upgrade`. What runs in the cluster is then whatever was last installed
from whichever checkout, and nothing records it. Phase 4's goal is the
opposite: git is the record of what runs, a change is a pull request, and a
rollback is a revert.

What this repository needs from the tool:

- it pulls from GitHub, because nothing on GitHub can reach a laptop;
- it installs Helm charts, ours and the platform's, with values from git;
- one place defines every environment, and a new environment is a small change;
- it is open source, free, and widely used in the job market;
- it fits beside FleetOps in the laptop's memory budget.

## Options considered

1. **Keep the scripts, run them from CI.** CI would need a way into the
   laptop's cluster, and the cluster would still only know what the last run
   did. No.
2. **Flux.** A CNCF graduated GitOps tool, pull-based, Helm-capable, with a
   small footprint. Equally valid; it has no built-in UI.
3. **Argo CD.** Also CNCF graduated and pull-based, renders Helm charts itself,
   shows every application's sync and health in a UI, and generates
   applications from lists (ApplicationSet). The most common GitOps tool in
   job postings.

## Decision

Argo CD (option 3), with this layout:

- **Bootstrap, once per cluster:** `make gitops-bootstrap` installs Argo CD
  from its Helm chart (the checksum is in `infra/local/versions.env`), gives it
  a read-only deploy key for this repository and gives each FleetOps namespace a
  pull secret for GHCR. It then applies one root Application. That is the only
  step run by hand.
- **App-of-apps:** the root application syncs `deploy/gitops/apps`, which holds
  everything else, in sync waves. Argo CD gets a health check for Applications
  so that a wave waits for the previous one to be healthy.
  - wave -2: two AppProjects. `platform` may change the whole cluster.
    `fleetops` may only change `fleetops-*` namespaces, and no cluster-wide
    resources.
  - wave -1: Argo CD, managing its own release from the same chart and values.
  - wave 0: Envoy Gateway and CloudNativePG, from the charts and values
    `cluster-up.sh` uses, so Argo CD adopts those releases without changing
    them. CloudNativePG moved from its release manifest to its Helm chart for
    this.
  - wave 1: the gateway and the namespaces from `infra/local`.
  - wave 2: FleetOps, as two ApplicationSets, one for the databases and one
    for the services, with one application per environment and component.
- **Environments:** `dev` and `staging`, both as namespaces on the kind cluster
  until Phase 6 gives `staging` a cloud cluster. An environment is a list entry
  in the ApplicationSets and one values file, `deploy/gitops/envs/<env>`, with
  its image tag and hostname.
- **Versions pinned twice, checked once:** the platform charts' versions are
  in `versions.env`, which the scripts install from, and in the applications.
  `deploy/gitops/check-versions.sh` fails CI when the two differ.

## Consequences

- What runs is what is on `main`. A manual change in the cluster is undone at
  the next sync (self-heal), and `make cluster-deploy` refuses to run once Argo
  CD is installed. The quick loop of building on the Mac and installing by hand
  stays, but on a cluster without Argo CD; CI keeps using it to test charts on
  every pull request.
- Changes arrive within Argo CD's polling interval, three minutes by default.
- Two credentials exist outside git: the deploy key and a classic GitHub token
  with only `read:packages`. GHCR has no fine-grained tokens. Both stay in
  `~/.config/fleetops` and in the cluster; the bootstrap checks the token's
  scope instead of trusting it.
- Argo CD costs memory: five pods, with dex and notifications turned off.
  `staging` costs a second database and three more services. Both are measured
  on the Mac before this merges.
- The services in an environment start in any order and settle: fleet-api waits
  for the database, telemetry-ingest restarts until the migrations ran. The
  probes keep traffic away meanwhile. Argo CD's progressive sync could order
  them, but it is still an alpha feature.
- Package 5 changes one file to move `dev` to a new image, and promotion is a
  pull request that copies a tag from one environment file to the next (ADR-0011).
