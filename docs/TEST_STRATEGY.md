# Test strategy

How FleetOps is tested: which kinds of tests exist, what each one proves, where
it runs, and what has to be true before a change merges or a version ships.
It describes the repository as it is at the end of Phase 3; later phases add
the layers listed under "Not covered yet".

## The layers

From the bottom, where tests are many, fast and narrow, to the top, where they
are few, slow and close to what the customer does:

| Layer | Lives in | Tools | Runs against | Proves |
|-------|----------|-------|--------------|--------|
| Static checks | every service | `gofmt`, `go vet`, golangci-lint; the Java compiler | the source | code that cannot pass review never reaches a test |
| Unit and slice tests | `services/*` (`*Test`, `*_test.go`) | JUnit 6, Mockito, Spring `@WebMvcTest`; `go test -race` | nothing: mocks and fakes | logic and edge cases, one class or function at a time |
| Integration tests | `services/fleet-api` (`*IT`), `services/telemetry-ingest` (`-tags=integration`) | Testcontainers, testcontainers-go | a real Postgres 17 with the real Flyway migrations | SQL, mappings, migrations and constraints behave as the code assumes |
| Acceptance tests | `tests/acceptance` | Cucumber + REST Assured, pytest-bdd, Robot Framework (ADR-0006) | the whole stack over HTTP, started with Compose | every acceptance criterion of every customer issue |
| End-to-end tests | `tests/e2e` | Playwright for TypeScript and for Java (ADR-0007), in Chromium, Firefox and WebKit | the whole stack through a browser | an operator can do the same through the dashboard |
| Security checks | `.github/workflows/security.yml` | gitleaks, Trivy, govulncheck | history, config files and the built images | no secrets, no fixable HIGH or CRITICAL vulnerability, no misconfiguration (`docs/SECURITY_POLICY.md`) |

The rule for choosing a layer: test a behaviour at the lowest layer that can
see it. A rounding rule for rollout targets is a unit test. That Postgres
rejects a pause reason on a running rollout is an integration test. That a
fleet operator can pause a rollout and see why is an acceptance test, and that
they can do it in the dashboard is an end-to-end test. A behaviour tested high
up because it was easier to write there makes the suite slower and the failure
harder to read.

Acceptance and end-to-end tests import no service code. They see only what the
customer sees, so the same suites can later run against the kind cluster
(Phase 4) without change.

## What runs when

| Trigger | What runs | Why |
|---------|-----------|-----|
| Pull request | `fleet-api.yml` and `go-services.yml` when their service changed; `acceptance.yml`, `e2e.yml`, `security.yml` and the image builds in `release.yml` always | the fast layers only where code changed; the system-level checks on every change, because a result about the system does not depend on which files moved |
| Push to `main` | the same, then the images are published to GHCR as `:main` and `:sha-<commit>` (ADR-0005) | what ships is what was tested, tagged with the commit |
| Nightly, UTC | acceptance at 03:23, e2e at 03:43, security at 04:17 | base images and vulnerability databases change without a commit; a nightly run finds that before the next pull request does |
| Release | merging the release-please pull request tags the version and publishes the images as `:<version>` | a release is a commit that already passed everything above on `main` |

The nightly times are off the hour on purpose: GitHub delays schedules that
start at :00.

## What must be green to merge

Nothing merges with a red check. The checks that carry rules of their own:

- **Coverage floors.** fleet-api 63% of lines (JaCoCo, unit tests only),
  telemetry-ingest 50%, vehicle-sim 33%. Each floor sits just under the
  current value: deleting tests breaks the build, and the floor only moves up.
- **Traceability.** Every customer issue is covered by the acceptance suites
  (a Gherkin scenario and a Robot test) or by the browser suites (a TypeScript
  and a Java test), or is listed in `tests/acceptance/coverage-exceptions.txt`
  with a reason. Suites of the same layer must have the same tests. See
  `tests/acceptance/README.md`.
- **Security.** Any gitleaks finding, a fixable HIGH or CRITICAL vulnerability
  in a shipped image, an end-of-life base OS, or a HIGH misconfiguration fails
  the build. Accepted risks live in `.trivyignore.yaml` with an expiry date.

GitHub does not enforce this yet: there is no branch ruleset with required
checks (deferred, see "Not covered yet"). Until there is, the routine before
every merge is `gh pr diff --name-only` to see that the pull request contains
what it should, then `gh pr checks --watch`.

## Test data

Tests that run against the stack never reset the database and never share
data. Each scenario registers its own vehicles with random VINs under a model
name no other test uses, and publishes its own release with a random version.
That is what lets the suites run in parallel, in any order, more than once, and
against a database that already holds data, including a long-lived environment
later on.

Unit tests build their data in code. fleet-api's integration tests share one
Postgres container with the real migrations applied, and every test starts
from empty tables: `PostgresIT` truncates them first, so no test depends on
the order the classes run in (EL-004).

## Flaky tests

No layer retries a failed test. A test that passes on a second try is reported
as what it is, a failure, and is fixed in the pull request that finds it or in
the one after. If it cannot be fixed at once, it is disabled in a pull request
that links a `bug` issue, never retried until it passes.

Tests wait for conditions, not for time. The browser tests use Playwright's
web-first assertions, the API tests check responses directly, and there is no
`sleep` in any suite.

## When a test fails

Every suite uploads its reports as a workflow artifact, kept 7 to 14 days:

| Layer | What to open |
|-------|--------------|
| Unit, integration | `fleet-api-test-reports` (Surefire and Failsafe XML), `fleet-api-jacoco`, `coverage-<service>` |
| Acceptance | `acceptance-<suite>`: the failing step with its full HTTP request and response in each report |
| End-to-end | `e2e-<suite>`: a Playwright trace and a screenshot per failed test; open the trace at https://trace.playwright.dev |
| Any job that started the stack | the "Service logs" step, printed when a job fails |

Problems worth remembering, with how they were found and fixed, go in
`docs/engineering-log.md`.

## Not covered yet

| Gap | Where it will be closed |
|-----|-------------------------|
| Load and throughput (#7: 500 vehicles at one message per second) | Phase 5, k6 |
| Contracts between services, checked without starting the whole stack | Phase 5 |
| The suites against Kubernetes instead of Compose | Phase 4, after each Argo CD sync |
| Resilience under failure | Phase 7, chaos tests |
| Required checks enforced by GitHub | branch ruleset, deferred |
| An HTML report for the Java browser suite | open; see ADR-0007 |
| Vehicle list paging without a stable order (#46), a stale page in the dashboard (#47) | bugs found while writing the e2e tests |
