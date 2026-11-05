# Security scanning policy

What runs, what blocks a merge, and what we knowingly accept. Written down so the
thresholds are a decision rather than whatever was convenient when a scan went red.

## What runs

| Scan | Tool | Scope | Blocks a merge |
|------|------|-------|----------------|
| Secrets | gitleaks | full git history and working tree | any finding |
| Dependencies and IaC | Trivy `fs` | `pom.xml`, `go.mod`, Dockerfiles, compose, Terraform | HIGH, CRITICAL with a fix |
| Go vulnerabilities | govulncheck | both Go modules | any reachable vulnerability |
| Container images | Trivy `image` | all three images | HIGH, CRITICAL with a fix |
| SBOM | Trivy (CycloneDX) | whole repository | never - it is an artifact |

All of them also run on a nightly schedule, because a clean scan last week says
nothing about today: the databases change daily, not the code.

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

## Review cadence

The nightly run is checked weekly. If a HIGH or CRITICAL finding has no fix for
more than 30 days, either the dependency gets replaced or the exception gets
written down here with a date.

## Not used, and why

**OWASP Dependency-Check.** Needs an NVD API key and a long database download to
be usable in CI; Trivy covers the same Maven and Go manifests without either. If
a future employer's process requires its report format specifically, it can be
added as a scheduled job rather than on the pull-request path.

**GitHub code scanning (SARIF upload).** Requires a public repository or GitHub
Advanced Security. The reports upload as build artifacts instead; switch to
`github/codeql-action/upload-sarif` once the repository is public.
