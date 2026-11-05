# Security scanning policy

What runs, what blocks a merge, and what we knowingly accept. Written down so the
thresholds are a decision rather than whatever was convenient when a scan went red.

## What runs

| Scan | Tool | Scope | Blocks a merge |
|------|------|-------|----------------|
| Secrets | gitleaks | full git history and working tree | any finding |
| IaC and config | Trivy `fs` (`misconfig,secret`) | Dockerfiles, compose, Terraform, Kubernetes manifests | HIGH, CRITICAL |
| Go vulnerabilities | govulncheck | both Go modules | any reachable vulnerability |
| Container images | Trivy `image` | all three images | HIGH, CRITICAL with a fix, minus `.trivyignore.yaml` |
| SBOM | Trivy (CycloneDX, per image) | the three shipped images | never - it is an artifact |

All of them also run on a nightly schedule, because a clean scan last week says
nothing about today: the databases change daily, not the code.

## Where dependency vulnerabilities are caught, and where they are not

Dependency manifests - `pom.xml`, `go.mod` - are **not** vulnerability-scanned.
That is deliberate and it cost us a red build to learn:

> `FATAL remote Maven repository returned 429 Too Many Requests for
> .../spring-data-bom-2026.0.1.pom. Retry-After: 1800.`

To turn a `pom.xml` into a dependency list, Trivy has to fetch the parent POM and
every imported BOM from Maven Central, which rate-limits anonymous bulk requests
and then blocks the IP for half an hour. A scan that depends on an unauthenticated
third-party repository being in a good mood is not a gate, it is a coin flip.

The replacement is better, not just more reliable. Dependency vulnerabilities are
caught in two places:

- **The image scan** reads `app.jar` and the Go binaries that actually ship.
  A manifest lists what was *requested*; an image contains what was *resolved*,
  transitive dependencies and all, with no network needed.
- **govulncheck** covers the Go modules with call-graph analysis, so it reports a
  CVE only when the vulnerable function is reachable from our code.

The contrast between the two Go scanners is worth keeping in mind. On the first
run, Trivy reported eleven `golang.org/x/crypto` CVEs in telemetry-ingest, all of
them in `x/crypto/ssh`; govulncheck reported none of them, because nothing in this
service speaks SSH - the module is pulled in for PBKDF2 during Postgres SCRAM
authentication. Trivy answers "is a vulnerable version present"; govulncheck
answers "can it be reached". Both questions are legitimate; an auditor asks the
first, an engineer prioritises with the second.

## Accepted risks

**Unfixed vulnerabilities do not block merges** (`--ignore-unfixed`). A CVE in a
base image with no patched version available gives the pull request nothing to
act on, and a gate that cannot be satisfied gets switched off within a week. The
nightly run still reports them, so they surface when a fix appears.

**MEDIUM and below are reported, not enforced.** Raising the bar is a decision to
make when HIGH and CRITICAL are consistently clean, not before.

**`curl` in the fleet-api runtime image** is there for the compose health check
and widens the package surface Trivy scans. Phase 4 moves health checking to
Kubernetes probes, and the layer can go then.

### Named exceptions

Everything in `.trivyignore.yaml` is listed here with its reasoning. Each entry is
scoped to a path and carries an `expired_at` date; when that date passes the
finding reappears and the decision has to be made again.

| Finding | Scope | Why | Expires |
|---------|-------|-----|---------|
| CVE-2026-78667, CVE-2026-78669, CVE-2026-97031 | `usr/bin/pebble` in the fleet-api image | Canonical's service manager, shipped inside `eclipse-temurin:21-jre` and compiled by Canonical with Go 1.26.7. The fix is a rebuild of the base image, which we do not control; the container's entrypoint is `java -jar app.jar`, so pebble is never executed. | 2027-01-15 |

An exception is for a finding that is **real and not ours to fix**. It is not for
a finding that is inconvenient. The test: can someone in this repository make the
finding go away? If yes, it gets fixed, not ignored.

## Triage log

First full run, 2026-10-16. Recorded because the shape of the findings is more
instructive than the count.

| Finding | Category | Action |
|---------|----------|--------|
| gitleaks, 12 commits | clean | none |
| Trivy `fs`, Maven Central 429 | broken scan, not a finding | dropped manifest vuln scanning; see above |
| govulncheck: `x/text` ×2, `pgx` SQL injection (reachable) | our dependency, fix available | bumped `pgx`, `x/text`, `x/crypto` in telemetry-ingest |
| `app.jar`: Tomcat ×3 CRITICAL, Jackson ×5 HIGH | our dependency, fix available | bumped the Spring Boot parent, which manages both |
| telemetry-ingest / vehicle-sim: 36 and 22 `stdlib` findings | our build input, fix available | bumped the builder image from `golang:1.24-alpine` to `1.27-alpine` |
| `libcrypto3` 3.3.7-r1 | base image lagging its own package repo | `apk upgrade --no-cache` in the runtime stage |
| `usr/bin/pebble` ×3 | upstream base image, not fixable here | exception with an expiry date |

The lesson in column two: "a CVE appeared" is never one problem. The useful
question is who can act - us, our dependency, or our base image supplier - and the
answer determines whether the output is a commit, a version bump or a dated note.

## Review cadence

The nightly run is checked weekly. If a HIGH or CRITICAL finding has no fix for
more than 30 days, either the dependency gets replaced or the exception gets
written down here with a date.

Separately, the pinned versions that are *security inputs* get reviewed on the
same cadence, because nothing makes them fail loudly:

- the Go builder image tag in both Go Dockerfiles (decides the embedded stdlib)
- the Alpine release in both runtime stages (`docker run --rm alpine:latest cat /etc/alpine-release` shows the current series)
- the Spring Boot parent version (decides the Tomcat and Jackson versions)

Dependabot covers the first two as image updates; the third it covers as a Maven
parent update. The reason to review by hand anyway is that Dependabot proposes a
bump and a human still has to merge it.

## Not used, and why

**OWASP Dependency-Check.** Needs an NVD API key and a long database download to
be usable in CI; the image scan covers the same jars without either. If a future
employer's process requires its report format specifically, it can be added as a
scheduled job rather than on the pull-request path.

**GitHub code scanning (SARIF upload).** Requires a public repository or GitHub
Advanced Security. The reports upload as build artifacts instead; switch to
`github/codeql-action/upload-sarif` once the repository is public.
