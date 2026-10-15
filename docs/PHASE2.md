# Phase 2 — Continuous integration

Goal: a pull request cannot be merged unless the code builds, tests pass, coverage and
quality gates hold, and no secret or known-vulnerable dependency is in the diff. Images
are built multi-arch and pushed to GHCR from `main` only. Release `v0.2.0`.

Start this after Phase 1's packages are merged, so the pipeline has three services and
a compose stack to act on.

## Work packages

| # | Branch | Delivers |
|---|--------------------------------|---------------------------------------------|
| 1 | `ci/1-build-and-test`          | Per-service workflows with path filters, unit tests, caching |
| 2 | `ci/2-integration-tests`       | Testcontainers against real Postgres        |
| 3 | `ci/3-quality-gates`           | JaCoCo, SonarCloud, golangci-lint, required checks |
| 4 | `ci/4-security-scans`          | Gitleaks, Dependency-Check, Trivy, SBOM     |
| 5 | `ci/5-images-and-versioning`   | Multi-arch buildx to GHCR, release-please   |
| 6 | `ci/6-jenkins-side-quest`      | Same build as a Jenkinsfile, run locally    |

## 1. Build and test

Two workflows: `.github/workflows/fleet-api.yml` and `go-services.yml`, each with

```yaml
on:
  pull_request:
    paths: [ "services/fleet-api/**", ".github/workflows/fleet-api.yml" ]
  push:
    branches: [ main ]
    paths: [ "services/fleet-api/**" ]
```

Path filters are what keep a monorepo pipeline fast — a Go change must not rebuild the
Java service. Use `actions/setup-java` with `cache: maven` and `actions/setup-go` with
`cache: true`; without caching every run re-downloads the dependency tree.

Runners are `ubuntu-latest` (amd64). Your Mac is arm64, so CI is the first place the
code runs on the target architecture. Expect the first surprise there.

## 2. Integration tests with Testcontainers

Add Testcontainers (`postgresql`, `junit-jupiter`) to `fleet-api` and write
`VehicleRepositoryIT` against a real Postgres 17 container: unique VIN violation,
pagination, the `findByModel` query. These prove what mocks cannot — that the mapping,
the schema and the Flyway migration agree.

Naming: `*Test` runs in `mvn test` (unit), `*IT` runs in `mvn verify` via failsafe
(integration). Keep them separate so a slow database test never blocks the fast loop.

For Go, use the Testcontainers Go module for the ingest service's store package.

Locally on Colima, Testcontainers needs:

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

Put those in the README, not in your shell profile only — anyone cloning the repo hits
this.

## 3. Quality gates

- JaCoCo with a `check` rule failing under 70% line coverage on `fleet-api`.
- `go test -race -coverprofile` for the Go services; fail the job under 70%.
- golangci-lint (`errcheck`, `govet`, `staticcheck`, `revive`).
- Spotless or Checkstyle on Java with Google or Palantir style, `mvn spotless:check` in CI.
- SonarCloud (free for public repos) with the default quality gate; feed it the JaCoCo
  report. If you keep the repo private, run SonarQube Community in a container instead
  and skip the PR decoration.
- Then make these checks **required** in the branch ruleset for `main`. Until that is
  set, the gate is advisory. This is the step that turns Phase 0's convention into a
  control.

## 4. Security scans

| Tool | What it catches | Fails the build on |
|---|---|---|
| Gitleaks | Secrets in the diff and history | any finding |
| OWASP Dependency-Check | Known CVEs in Java dependencies | CVSS >= 7 |
| `govulncheck` | Known CVEs in Go modules | any reachable vuln |
| Trivy (fs + image) | OS and library CVEs, IaC misconfig | HIGH, CRITICAL |
| Syft | Generates SBOM (SPDX or CycloneDX) | never — it's an artifact |

Upload SARIF from Trivy and Gitleaks to GitHub code scanning so findings show up on the
PR instead of only in logs. Enable Dependabot for Maven, Go modules and GitHub Actions.

Expect noise in the first run: base image CVEs with no fix available. Decide a policy
(ignore unfixed, with a review date) and write it in `docs/SECURITY_POLICY.md` rather
than lowering the threshold silently.

## 5. Images and versioning

Build only on `main`, never on PRs from forks:

```yaml
- uses: docker/setup-buildx-action@v3
- uses: docker/build-push-action@v6
  with:
    context: services/fleet-api
    platforms: linux/amd64,linux/arm64
    push: true
    tags: |
      ghcr.io/brianvisas/fleet-api:${{ github.sha }}
      ghcr.io/brianvisas/fleet-api:latest
    cache-from: type=gha
    cache-to: type=gha,mode=max
```

Authenticate with the built-in `GITHUB_TOKEN` and `packages: write` permission — no PAT.
Tag with the commit SHA; `latest` is a convenience, never what a deployment references
(Kyverno blocks it in Phase 8).

Add release-please to derive versions and a changelog from Conventional Commits. It
opens a release PR; merging it tags the release. That is how `v0.2.0` gets created.

Sign images with Cosign (keyless, OIDC) if you want Phase 8's signature policy to have
something to verify.

## 6. Jenkins side quest

Run Jenkins LTS in a container, install the Docker and Pipeline plugins, and write a
`Jenkinsfile` reproducing the Java build: checkout, `mvn verify`, publish JUnit and
JaCoCo reports, build the image. You are not maintaining it — you are learning where
declarative pipeline syntax, agents and credentials differ from GitHub Actions, because
plenty of German enterprises still run Jenkins and will ask.

## Done when

- [ ] A PR touching only Go does not run the Java pipeline, and vice versa
- [ ] A PR with a failing test, coverage drop, lint error, leaked secret or HIGH CVE
      cannot be merged
- [ ] Integration tests run against a real Postgres in CI
- [ ] `main` pushes publish multi-arch images to GHCR with an SBOM attached
- [ ] A Jenkinsfile builds `fleet-api` locally
- [ ] Branch ruleset requires the status checks
- [ ] Tag `v0.2.0` via release-please
