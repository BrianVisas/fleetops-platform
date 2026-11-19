# Engineering log

Problems hit while building FleetOps, what caused them, and how each fix was
proven. Decisions with alternatives on the table go in `docs/adr/`; this file is
for things that went wrong.

Every entry follows the same structure:

- **Symptom**: what was observed, with the exact message where there is one
- **Root cause**: the mechanism, not just the trigger
- **Fix**: what changed, with the PR or commit
- **Verification**: how we know it is fixed. Evidence, not assertion.
- **Lesson**: the rule that carries over to the next project

## Index

| ID | Problem | Area | Status |
|----|---------|------|--------|
| [EL-001](#el-001-spring-boot-4-moved-the-test-slices) | Spring Boot 4 moved the test slices | build | fixed |
| [EL-002](#el-002-testcontainers-2x-unmanaged-and-renamed) | Testcontainers 2.x unmanaged and renamed | build | fixed |
| [EL-003](#el-003-testcontainers-could-not-find-docker-on-colima) | Testcontainers could not find Docker on Colima | environment | fixed |
| [EL-004](#el-004-ci-red-local-green-integration-tests-depended-on-class-order) | CI red, local green: integration tests depended on class order | testing | fixed |
| [EL-005](#el-005-a-coverage-gate-that-passed-without-measuring-anything) | A coverage gate that passed without measuring anything | CI | fixed |
| [EL-006](#el-006-a-lint-config-that-contradicted-its-own-comment) | A lint config that contradicted its own comment | CI | fixed |
| [EL-007](#el-007-maven-central-rate-limiting-took-down-the-dependency-scan) | Maven Central rate limiting took down the dependency scan | security | fixed |
| [EL-008](#el-008-22-cves-in-a-binary-with-no-dependencies) | 22 CVEs in a binary with no dependencies | security | fixed |
| [EL-009](#el-009-operation-not-permitted-even-with-sudo) | "Operation not permitted", even with sudo | environment | worked around |
| [EL-010](#el-010-pushes-that-ci-never-saw) | Pushes that CI never saw | workflow | fixed |
| [EL-011](#el-011-the-gate-and-the-report-measured-different-things) | The gate and the report measured different things | CI | fixed |
| [EL-012](#el-012-stale-coverage-data-made-a-correct-fix-look-wrong) | Stale coverage data made a correct fix look wrong | CI | no code change |
| [EL-013](#el-013-main-could-not-build-the-telemetry-ingest-image) | main could not build the telemetry-ingest image | build | fixed |
| [EL-014](#el-014-ci-would-have-scanned-with-an-old-go) | CI would have scanned with an old Go | CI | fixed |
| [EL-015](#el-015-zero-findings-on-an-unsupported-os) | Zero findings on an unsupported OS | security | fixed |
| [EL-016](#el-016-a-pinned-action-version-that-no-longer-existed) | A pinned action version that no longer existed | supply chain | fixed |
| [EL-017](#el-017-five-changes-appeared-twice-in-the-first-changelog) | Five changes appeared twice in the first changelog | release | fixed |
| [EL-018](#el-018-ten-dependency-prs-in-24-seconds) | Ten dependency PRs in 24 seconds | dependencies | fixed |
| [EL-019](#el-019-ci-and-the-image-built-with-different-maven-versions) | CI and the image built with different Maven versions | build | fixed |
| [EL-020](#el-020-a-file-nobody-had-touched-showed-as-modified) | A file nobody had touched showed as modified | build | fixed |
| [EL-021](#el-021-a-health-check-that-could-never-pass) | A health check that could never pass | build | fixed |
| [EL-022](#el-022-two-pull-requests-each-red-because-of-the-other) | Two pull requests, each red because of the other | CI | fixed |
| [EL-023](#el-023-twenty-minutes-of-fonts-and-codecs) | Twenty minutes of fonts and codecs | CI | fixed |
| [EL-024](#el-024-a-wait-that-never-waited) | A wait that never waited | build | fixed |
| [EL-025](#el-025-a-time-limit-sized-on-ci) | A time limit sized on CI | build | fixed |
| [EL-026](#el-026-two-ci-jobs-lost-to-one-refused-download) | Two CI jobs lost to one refused download | CI | superseded by EL-027 |
| [EL-027](#el-027-the-retry-that-could-not-help) | The retry that could not help | CI | fixed |
| [EL-028](#el-028-a-cluster-living-in-swap) | A cluster living in swap | local cluster | fixed |
| [EL-029](#el-029-a-check-that-only-passed-once) | A check that only passed once | local cluster | fixed |
| [EL-030](#el-030-a-cache-that-held-back-security-fixes) | A cache that held back security fixes | CI, security | fixed |
| [EL-031](#el-031-a-dependency-layer-the-cache-kept-losing) | A dependency layer the cache kept losing | CI | fixed |

---

## EL-001: Spring Boot 4 moved the test slices

Phase 1 · build · fixed on `feat/2-vehicles-api`

**Symptom.** The first controller test did not compile: `@WebMvcTest` could not
be found. Tests using `com.fasterxml.jackson.databind.ObjectMapper` failed the
same way.

**Root cause.** The project was generated with Spring Boot 4, and the test code
was written from Spring Boot 3 habits. Boot 4 split test auto-configuration into
per-area starters: `@WebMvcTest` now lives in
`org.springframework.boot.webmvc.test.autoconfigure` and needs
`spring-boot-starter-webmvc-test`. Jackson 3 moved from `com.fasterxml.jackson`
to `tools.jackson`.

**Fix.** Added the starter and the new import. Controller tests build request
bodies with Java text blocks instead of an `ObjectMapper`, which removes the
Jackson dependency from the tests entirely.

**Verification.** All four `VehicleControllerTest` cases compile and pass.

**Lesson.** Check which version was actually generated before applying
conventions from memory. A major version is allowed to move things.

---

## EL-002: Testcontainers 2.x unmanaged and renamed

Phase 2 · build · fixed in PR #10

**Symptom.** `'dependencies.dependency.version' for org.testcontainers:... is
missing.` Adding a version from a search result produced a release that did not
match the current artifact names.

**Root cause.** Spring Boot 4 no longer manages the Testcontainers version, so
the pom had to declare it. The Maven search API suggested 1.21.3; the
repository's own `maven-metadata.xml` showed 2.0.5 as current. Testcontainers
2.x renamed both artifacts (`testcontainers-postgresql`) and packages
(`org.testcontainers.postgresql.PostgreSQLContainer`, which is also no longer
generic).

**Fix.** Imported `testcontainers-bom` 2.0.5 in `dependencyManagement`, switched
to the new artifact and package, and dropped the unused JUnit module by starting
the container in a static initialiser.

**Verification.** All ten integration tests run against a real Postgres 17, both
locally and in CI.

**Lesson.** For "what is the latest version", read the repository metadata, not
a search index. And read the release notes of a major version before upgrading
through it.

---

## EL-003: Testcontainers could not find Docker on Colima

Phase 2 · environment · fixed by configuration

**Symptom.** `Could not find a valid Docker environment`, although
`docker ps` worked in the same terminal.

**Root cause.** Two different consumers of the Docker socket. The Testcontainers
client on macOS looks for `/var/run/docker.sock`, but Colima's socket lives at
`~/.colima/default/docker.sock`. Separately, Testcontainers' Ryuk sidecar mounts
the socket path *inside* the Colima VM, where the macOS path does not exist.

**Fix.**

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

The first tells the client where the socket is on the host; the second tells
Ryuk where it is inside the VM.

**Verification.** The log shows `Resolved dockerHost=unix:///Users/.../.colima/default/docker.sock`
and `Ryuk started`.

**Lesson.** When a container tool "can't find Docker", ask which process is
looking, and from which side of the VM boundary.

---

## EL-004: CI red, local green: integration tests depended on class order

2026-10-16 to 2026-11-06 · testing · fixed in PR #10, commit `b36b9f2`

**Symptom.** The first CI run of the integration tests failed with four errors,
all in `VehicleRepositoryIT.clean()`:

```
ERROR: update or delete on table "vehicle" violates foreign key constraint
"rollout_target_vehicle_id_fkey" on table "rollout_target"
```

The same suite passed on the development machine.

**Root cause.** All integration tests share one Postgres container, which is the
right call for speed. `SchemaMigrationIT.rolloutTargetKeepsTheRolloutVehiclePairUnique`
inserts a vehicle, a release, a rollout and a `rollout_target` row and leaves
them there. CI happened to run the classes in the order `FleetApiApplicationIT`,
`SchemaMigrationIT`, `VehicleRepositoryIT`, so `repository.deleteAll()` on
`vehicle` hit the foreign key. Locally the order was different and the problem
never surfaced.

**Fix.** `PostgresIT` truncates every table in `public` before each test, with
`CASCADE` for the foreign keys. The table list is read from `pg_tables`, so a
table added by a future migration is covered automatically. `flyway_schema_history`
is excluded: truncating it would make Flyway treat the schema as unmigrated, and
`SchemaMigrationIT` asserts on its contents.

**Verification.** Locally, `./mvnw verify` with `-Dfailsafe.runOrder=alphabetical`
and with `reversealphabetical`, both green. CI run `42195452684` green, with all
ten integration tests listed, in yet another order.

**Lesson.** One green run does not prove a test suite is order-independent, it
proves one order works. Each test establishes its own preconditions; nothing may
assume the database starts empty unless something makes it so.

---

## EL-005: A coverage gate that passed without measuring anything

2026-10-16 to 2026-11-06 · CI · fixed in PR #12 (replacing #11)

**Symptom.** The pull request that introduced a 60% line-coverage floor for
fleet-api went green in 47 seconds. Nobody had checked what the real coverage
was, and as it turned out, nothing had measured it.

**Root cause.** The `argLine` carrying `@{argLine}` was configured on
`spring-boot-maven-plugin`, which ignores it. Surefire therefore ran without the
JaCoCo agent, no `jacoco.exec` was written, and `jacoco:check` skipped itself for
lack of execution data. A skipped check does not fail the build. The 60% floor
had also been chosen by estimate, not from a measurement.

**Fix.** Moved `argLine` to `maven-surefire-plugin`. Measured unit-test line
coverage (64.7%) and set the floor one point below it, at 0.63.

**Verification.** `./mvnw clean verify -Dcoverage.minimum=0.99` now fails with
`lines covered ratio is 0.64, but expected minimum is 0.99`. At the real floor it
passes. CI on PR #12 prints `All coverage checks have been met.`

**Lesson.** A gate that has never been seen to fail has not been shown to work.
Test the gate itself, by forcing it to fail once, before relying on it. Floors
come from measurements.

---

## EL-006: A lint config that contradicted its own comment

2026-10-16 to 2026-11-06 · CI · fixed in PR #12, commit `4d4924e`

**Symptom.** golangci-lint failed vehicle-sim with six `errcheck` findings, every
one of them a deliberate discard:

```
client.go:165:2: Error return value of `response.Body.Close` is not checked (errcheck)
    _ = response.Body.Close()
```

**Root cause.** `.golangci.yml` set `errcheck.check-blank: true`, which reports
assignments to the blank identifier. The comment directly above it said the
opposite: that `_ = f()` was accepted. `_ =` is Go's explicit way of saying "this
error is ignored on purpose", and the setting left no way to express that short
of a `nolint` comment on every line.

**Fix.** errcheck runs on its defaults: an error return dropped entirely is
reported, an error assigned to `_` is not.

**Verification.** The vehicle-sim lint step passes on PR #12. Nothing in the
code changed.

**Lesson.** A comment in a config file is a claim, and claims get tested. A lint
rule with no way to express intent ends up silenced line by line, which is worse
than not running it.

---

## EL-007: Maven Central rate limiting took down the dependency scan

2026-10-16 to 2026-11-06 · security · fixed in the security PR (`ci/4-security-scans`)

**Symptom.** The first local run of the security scans aborted in the Trivy
filesystem scan:

```
FATAL remote Maven repository returned 429 Too Many Requests for
.../spring-data-bom-2026.0.1.pom. Retry-After: 1800.
```

**Root cause.** To vulnerability-scan a `pom.xml`, Trivy rebuilds the dependency
graph by fetching the parent POM and every imported BOM from Maven Central. That
is dozens of anonymous requests in a burst; Maven Central answered with a rate
limit and blocked the IP for half an hour.

**Fix.** The filesystem scan now runs only `misconfig,secret`. Dependency
vulnerabilities come from two better sources: the container image scan, which
reads the jars and binaries that actually ship, and govulncheck, which reports
only vulnerabilities reachable from our code. SBOMs are generated per image.

That fix rested on an assumption that turned out to be wrong: that with the
vulnerability scanner off, Trivy would leave `pom.xml` alone. The second run, on
2026-11-06, hit the same 429 for `spring-batch-bom-6.0.6.pom`. Trivy parses
`pom.xml` while inspecting the tree, whichever scanners are enabled. Trivy's
Java documentation gives the switch that actually stops it: "You can disable
connecting to the maven repository with the `--offline-scan` flag." The
filesystem scan now runs with it, as `TRIVY_OFFLINE_SCAN` in CI.

**Verification.** The scan on 2026-11-06 completed the filesystem stage with no
429 and reported 0 misconfigurations in all three Dockerfiles. The only side
effect is an expected warning that pom dependency versions cannot be determined.

**Lesson.** A gate must not depend on an unauthenticated third party being in a
good mood. And scan what you ship: a manifest lists what was requested, an image
contains what was resolved. Also: a fix built on an untested assumption is a
hypothesis. This one needed a second run to become a fix.

---

## EL-008: 22 CVEs in a binary with no dependencies

2026-10-16 to 2026-11-06 · security · fixed in the security PR (`ci/4-security-scans`)

**Symptom.** The image scan reported 22 HIGH findings for vehicle-sim and 36 for
telemetry-ingest. vehicle-sim has no third-party dependencies at all.

**Root cause.** Nearly every finding was in `stdlib` v1.24.13. Go compiles the
standard library statically into the binary, so the builder image's Go version,
`golang:1.24-alpine`, decided which vulnerabilities shipped. The Alpine runtime
image also carried `libcrypto3` 3.3.7-r1 while r2 was already in Alpine's
package repository.

**Fix.** Builder image moved to `golang:1.27-alpine`; the runtime stage runs
`apk upgrade --no-cache`. In telemetry-ingest, govulncheck's three reachable
findings were fixed by upgrading pgx to 5.12.0 and x/text to 0.43.0. The Alpine
side turned out to need more than a package upgrade; see EL-015.

**Verification.** On 2026-11-06 both Go images scanned with 0 findings, in the
`gobinary` layer as well as the OS layer, and govulncheck reported no
vulnerabilities in either module.

**Lesson.** The builder image tag is a security input, not a convenience. It
belongs on the same review cadence as dependency versions.

---

## EL-009: "Operation not permitted", even with sudo

2026-11-05 · environment · worked around

**Symptom.** `bash ~/Downloads/install-security-triage.sh` failed with
`Operation not permitted`, and so did the same command under `sudo`. `ls -lO@` on
the file worked; `xattr -c` on it failed the same way.

**Root cause.** macOS TCC (Transparency, Consent and Control) had withdrawn
Terminal's access to the Downloads folder. TCC blocks content operations
(`open`, `setxattr`) but allows metadata operations (`stat`, `listxattr`), which
is why `ls` worked. It authorises the application rather than the user ID, so
`sudo` changes nothing. The error was `EPERM`, not `EACCES`, which already ruled
out ordinary Unix permissions.

**Fix.** Copied the file out of Downloads with Finder, which acts on direct user
action. The permanent fix is re-granting Terminal access under Privacy &
Security, Files and Folders.

**Verification.** The script ran from the new location.

**Lesson.** `EPERM` and `EACCES` come from different layers. Read which one you
got before reaching for `sudo`.

---

## EL-010: Pushes that CI never saw

2026-11-06 · workflow · fixed

**Symptom.** After fixes were pushed to `ci/2-integration-tests` and
`ci/3-quality-gates`, `gh pr checks` kept showing results from 16 October, and no
new runs appeared.

**Root cause.** Both pull requests, #10 and #11, had been closed (not merged) on
2026-11-05, three seconds apart. The issues events API showed they were closed
from the repository owner's own account. A closed pull request does not track
new pushes, so no CI ran. #10 reopened normally. #11 could not be reopened,
`GraphQL: Could not open the pull request`, because its branch had been
force-pushed by a rebase while it was closed, and GitHub refuses that.

**Fix.** Reopened #10. Opened #12 from the same branch to replace #11, and
linked the two with a comment.

**Verification.** New runs appeared against the new commit SHAs and were checked
by run ID.

**Lesson.** When "CI didn't run", first compare the run IDs and SHAs being shown
with the commit just pushed, and check the state of the pull request, before
debugging the workflow itself. Answer "who did this" from the audit trail rather
than by guessing:

```bash
gh api repos/OWNER/REPO/issues/NUMBER/events \
  --jq '.[] | select(.event=="closed") | {by: .actor.login, at: .created_at}'
```

---

## EL-011: The gate and the report measured different things

2026-11-06 · CI · fixed in PR #12, commit `e81350b`

**Symptom.** The JaCoCo HTML report showed 64.7% line coverage; `jacoco:check`
reported 0.69 for the same build. The floor had been calculated from the first
number and was being enforced against the second. The integration tests also
still printed `Mockito is currently self-attaching`, even though surefire had the
Mockito agent configured.

**Root cause.** `jacoco:report` runs at the `test` phase, after the unit tests.
`jacoco:check` runs at `verify`, after the integration tests. Failsafe had no
`argLine` of its own, so it inherited the `argLine` property that JaCoCo's
`prepare-agent` sets. The integration tests ran with the coverage agent and added
their data to the same `jacoco.exec`, and they did not get the Mockito agent.
Both symptoms came from the same missing setting.

**Fix.** Failsafe gets an explicit `argLine` carrying only the Mockito agent. The
coverage gate now measures unit tests only, as documented.

**Verification.** `./mvnw clean verify -Dcoverage.minimum=0.99` reports `0.64`,
matching the report's 64.7%. No `self-attaching` line appears in either test
phase.

**Lesson.** Know what a gate actually measures. The clue here was two numbers
that should have matched and did not; it came from comparing the gate's own
output with the report, not from reading the configuration.

---

## EL-012: Stale coverage data made a correct fix look wrong

2026-11-06 · CI · no code change

**Symptom.** After the fix in EL-011, the self-attach warning disappeared, which
confirmed the fix took effect, but `jacoco:check` still reported 0.69.

**Root cause.** The JaCoCo agent appends to `target/jacoco.exec` by default, and
the local runs used `verify` rather than `clean verify`. The file still held
coverage from every earlier run, including those where the integration tests had
the agent. Coverage only merges upward: a line one old run covered stays covered.

**Fix.** None in code. Measurements are taken with `clean verify`. CI was never
affected, because every CI run starts from a fresh checkout.

**Verification.** The prediction was written down before running: "after `clean`
the gate will read 0.64". `clean verify` then reported exactly 0.64.

**Lesson.** Clean before you measure. A cached build artefact will happily answer
a question about old code. It is the same family as a Docker layer cache serving
an old base image, or the Go test cache serving a pass, which is why CI runs
`go test -count=1`.

---

## EL-013: main could not build the telemetry-ingest image

2026-11-06 · build · fixed in the security PR (`ci/4-security-scans`)

**Symptom.** On `main`, `docker build services/telemetry-ingest` failed at the
`go mod download` step: `process "/bin/sh -c go mod download" did not complete
successfully: exit code: 1`. Every CI check on `main` was green.

**Root cause.** PR #10 added testcontainers-go, whose minimum Go version moved
the `go` directive in `go.mod` from 1.24 to 1.25.0. The Dockerfile's builder
stage still used `golang:1.24-alpine`, and the official Go images set
`GOTOOLCHAIN=local`, so they will not fetch a newer toolchain to satisfy
`go.mod`. The captured output was cut off before Go's own message; the cause
follows from those two facts, and the fix below confirms it. CI never noticed
because it tests with `setup-go`, which reads `go.mod` and installs a matching
Go, and no workflow on `main` built the container image.

**Fix.** The builder stage moved to `golang:1.27-alpine`. The security workflow
builds all three images on every pull request, as part of the image scan, so a
Dockerfile that cannot build now fails a PR.

**Verification.** All three images build on the security branch, and the image
scan ran against each of them.

**Lesson.** CI only protects what it executes. If the artefact you ship is a
container image, build that image on every pull request. Testing the code with a
different toolchain from the one in the Dockerfile leaves exactly this gap.

---

## EL-014: CI would have scanned with an old Go

2026-11-06 · CI · fixed in the security PR (`ci/4-security-scans`)

**Symptom.** govulncheck failed both Go modules locally with one finding each:

```
Vulnerability #1: GO-2026-6809
    Distrust compromised intermediate certificates in crypto/x509
    Found in: crypto/x509@go1.27.2
    Fixed in: crypto/x509@go1.27.3
```

**Root cause.** govulncheck checks the standard library of the Go toolchain that
runs it, not the one in the Dockerfile. Locally that was go1.27.2. Looking at
what CI would have run turned up a bigger problem: `setup-go` with
`go-version-file` installs exactly the version in the `go` line when it has a
patch number, so telemetry-ingest (`go 1.26.0`) would have been tested and
scanned with Go 1.26.0, and vehicle-sim (`go 1.24`) with the latest 1.24.x, a
release line that no longer receives security fixes. setup-go reads a `toolchain`
line instead when there is one, but only from v6; the workflows used v5.

**Fix.** Both modules pin `toolchain go1.27.3` in `go.mod`, and both workflows
moved to `actions/setup-go@v6`. The `go` line stays the minimum language
version; the `toolchain` line decides the Go that builds, tests and scans.
Dependabot's `gomod` updates propose a new toolchain line when a patch release
appears, so the pin does not quietly go stale.

**Verification.** With the toolchain pinned, govulncheck reported `No
vulnerabilities found.` for both modules.

**Lesson.** A scanner's result is only as current as the inputs it runs on. When
a tool checks "your" standard library, find out whose standard library that
actually is, and in CI it may not be the one you expect.

---

## EL-015: Zero findings on an unsupported OS

2026-11-06 · security · fixed in the security PR (`ci/4-security-scans`)

**Symptom.** Both Go images scanned with 0 vulnerabilities. In the same output:

```
WARN  This OS version is no longer supported by the distribution  family="alpine" version="3.21.10"
WARN  The vulnerability detection may be insufficient because security updates are not provided
```

The warning did not fail the scan, locally or in CI.

**Root cause.** Alpine 3.21 had reached end of life since the first scan in
October. Once a distribution stops publishing security updates, there are no
fixed versions to compare against, so a scanner finds nothing. Zero findings
here meant no data, not no vulnerabilities.

**Fix.** Both runtime stages moved to `alpine:3.24`, the current series, read
from `alpine:latest` and pinned to the minor version so that a new series only
arrives through a reviewable pull request. The image scan now runs with
`--exit-on-eol 1` (`TRIVY_EXIT_ON_EOL` in CI), so an end-of-life base OS fails
the scan instead of producing a reassuring zero.

**Verification.** The re-scan detected Alpine 3.24.4 with no end-of-life
warning, and every scan reported clean.

**Lesson.** Read the warnings, not just the count. The most dangerous scan result
is a clean one that has no data behind it.

---

## EL-016: A pinned action version that no longer existed

2026-11-06 · supply chain · fixed in the security PR (`ci/4-security-scans`)

**Symptom.** On the security workflow's first run on GitHub, every job using
`aquasecurity/trivy-action@0.36.0` failed in 2 to 7 seconds, during job setup,
before any step ran. The jobs that do not use the action passed.

**Root cause.** The tag `0.36.0` does not exist; the release is `v0.36.0`. The
workflow had been written from the action's older, unprefixed tag naming without
checking. The reason the naming changed is the more important finding: in
CVE-2026-33634, on 19 March 2026, an attacker force-pushed 76 of the action's 77
version tags to malicious commits that stole CI secrets, and the releases were
re-published afterwards. A version tag is a movable pointer. Anyone pinned to one
of those tags ran the attacker's code without changing a line of their workflow.

**Exposure.** None. The repository was created in September 2026, the security
workflow never ran on GitHub before this run, and the local `aquasec/trivy:latest`
pulls (October and November) were months after the malicious images were removed
from Docker Hub.

**Fix.** The action is pinned to the full commit SHA of `v0.36.0`, with the version
in a comment (`@ed142fd0673e97e23eac54620cfb913e5ce36c25 # v0.36.0`), which
Dependabot can still update. The local scan script pins `aquasec/trivy:0.76.2` and
`zricethezav/gitleaks:v8.30.0` instead of `:latest`.

**Verification.** The tag list of `aquasecurity/trivy-action` shows `v0.36.0` at
`ed142fd`. CI on the security PR runs the pinned action.

**Lesson.** Pin third-party actions to a commit SHA, not a tag. A tag that fails to
resolve fails loudly; a tag that has been moved runs whatever it now points to. And
the tools that guard the pipeline are part of its supply chain too.

---

## EL-017: Five changes appeared twice in the first changelog

2026-11-09 · release · fixed in the release cleanup PR

**Symptom.** The release notes and `CHANGELOG.md` for v0.2.0 listed five changes
twice, with different commits, for example:

```
* coverage gates and go linting (37e6c06)
* coverage gates and go linting (b96a690)
```

**Root cause.** Pull requests had been merged with merge commits. GitHub writes
the pull request's title into the merge commit's message, and those titles are
Conventional Commits, so release-please counted each change once for the original
commit and again for the merge commit. The first SHA of each pair (`186ba42`,
`37e6c06`, `6ed2463`, `7acfce6`, `62b257f`) is a merge commit.

**Fix.** The repository now allows squash merging only, with the pull request
title as the commit title and the individual commit messages kept in the body, so
each pull request lands as exactly one Conventional Commit. The five duplicate
lines were removed from `CHANGELOG.md` and from the v0.2.0 release notes. The
`6cd1af3` entry stays: it is a merge commit too, but the only one that describes
the security scanning pull request as a whole.

**Verification.** The repository settings report squash merging as the only
method. The release pull request for v0.2.0 itself landed as a single commit
(`85c86d5`), and the cleaned release notes list each change once.

**Lesson.** The merge strategy is part of the release pipeline. Any tool that
reads history, from changelog generators to `git bisect`, is only as clean as the
history it reads.

**Follow-up, 2026-11-10.** The fix was incomplete. Squash merges still put each
pull request's individual commit messages into the commit body, and
release-please reads Conventional Commit lines in a body as further changes.
The body of #30 began with Dependabot's own commit message,
`build(deps): bump the maven-build group`, which differs from the pull request
title, so the pending v0.2.1 changelog listed #30 twice. Squash commits now get
a blank body (`squash_merge_commit_message=BLANK`), so a pull request
contributes exactly its title. #30, already on `main`, has a
`BEGIN_COMMIT_OVERRIDE` block in its description, which release-please uses
instead of the commit message; the release pull request then listed it once.
The first commit merged with the new setting, `1e91c58`, has a title and no
body.

---

## EL-018: Ten dependency PRs in 24 seconds

2026-11-10 · dependencies · fixed in #29

**Symptom.** Merging the security scanning PR (#14) put `.github/dependabot.yml`
on `main` for the first time. Dependabot opened ten pull requests (#15 to #24)
between 20:26:52 and 20:27:16. Four bumped GitHub Actions one at a time, two
bumped the two halves of the same testcontainers-go release, and two proposed a
new JDK by different routes:

```
bump maven from 3.9-eclipse-temurin-21 to 3.9-eclipse-temurin-26
bump eclipse-temurin from 21-jre to 25-jre
```

**Root cause.** The first configuration had no groups, no cooldown and no ignore
rules, so Dependabot did what that configuration asks for: one pull request per
outdated dependency, for every release whatever its age, all at once because
everything was outdated at once. The two JDK pull requests show why one PR per
package is dangerous and not only noisy. Merged separately, they would have built
the jar on JDK 26, which is not an LTS release, and run it on JDK 25.

**Fix.** #29 groups updates by kind (`spring`, `maven-build`, `go-dependencies`,
`actions`), gives every ecosystem a 14-day cooldown, and ignores JDK majors, which
are an ADR decision. The file was validated against the SchemaStore schema for
`dependabot.yml`, together with a deliberately broken copy to prove the check
could fail. The two JDK pull requests were closed by hand.

**Verification.** Dependabot re-ran when #29 merged. It opened two grouped pull
requests, #30 (`maven-build`: the Maven wrapper to 3.10.0, JaCoCo to 0.8.16) and
#31 (`actions`: four updates), and closed the six they replaced. Neither JDK bump
came back. testcontainers-go 0.45.0, published on 2026-11-05, was held back by the
cooldown; its two pull requests were closed, and it returns as one grouped update
once it is 14 days old. #30 was re-verified locally before merging (20 unit and 10
integration tests, coverage checks met), and both groups merged with CI green.

**Lesson.** Configure Dependabot before its first run, not after. Its defaults
make sure no update is ever missed. A team needs updates it can review: grouped,
a little delayed, and with the decisions that belong to a person, like the JDK,
kept out of the routine flow.

---

## EL-019: CI and the image built with different Maven versions

2026-11-10 · build · fixed in the Maven wrapper image PR

**Symptom.** Triaging #30, which moved the Maven wrapper from 3.9.16 to 3.10.0,
showed that the fleet-api image did not use the wrapper at all. CI runs `./mvnw`;
the Dockerfile ran `mvn` from the `maven:3.9-eclipse-temurin-21` builder image.
Once #30 merged, CI built with Maven 3.10.0 and every published image with 3.9.x.

**Root cause.** One tool had two sources of truth, and only one of them was being
updated. Dependabot updates the wrapper through the `maven` ecosystem. The builder
image was on the ignore list on purpose (EL-018: the only bump it had proposed was
the JDK 26 tag), so its Maven could never leave 3.9. Nothing failed, because the
project builds with both versions, and that is exactly why the drift was
invisible.

**Fix.** The image builds with the wrapper. The builder is now
`eclipse-temurin:21-jdk`, a plain JDK, and the build stage runs `./mvnw` against
the same `.mvn/wrapper/maven-wrapper.properties` as CI, printing the version with
`./mvnw -v` first. The `maven` ignore rule left `dependabot.yml` with the image.
The existing `eclipse-temurin` rule now covers the builder and the runtime stage
together, so a JDK major cannot reach one stage without the other.

**Verification.** The image build log prints `Apache Maven 3.10.0` from the
version step, the version in `maven-wrapper.properties`. The pull request's
multi-arch image build and the security workflow's image scan both passed with
the new build stage.

**Lesson.** Pin a tool in one place and have every environment read it from
there. "CI and the image use the same Maven" had been true by coincidence, and
coincidences are what dependency updates break.

---

## EL-020: A file nobody had touched showed as modified

2026-11-10 · build · fixed in #32

**Symptom.** After pulling `main`, `git status` reported
` M services/fleet-api/mvnw.cmd` with every line changed (189 insertions, 189
deletions), in a checkout where nobody had edited the file. The installer for
#32 refuses to run on a dirty tree, so the first local image build quietly used
the old Dockerfile from `main`.

**Root cause.** `services/fleet-api/.gitattributes`, generated by Spring
Initializr, said `*.cmd text eol=crlf`: store the file with LF line endings in
the repository and check it out with CRLF. Dependabot's #30 wrote `mvnw.cmd`
through the GitHub API exactly as the Maven wrapper ships it, with CRLF, so the
conversion never ran. `git ls-files --eol` showed
`i/crlf  w/crlf  attr/text eol=crlf`: git converts the working copy to LF before
comparing, the stored copy has CRLF, and the two can never match.
`git diff --ignore-cr-at-eol` showed no difference at all.

**Fix.** The rule is now `*.cmd -text`. `mvnw.cmd` is stored byte for byte as
the wrapper ships it, with no conversion, so what Dependabot writes and what git
expects are the same thing. The file's content did not change.

**Verification.** Reproduced in a scratch repository first: a CRLF blob
committed past the attribute showed the same ` M` after a fresh checkout, and
with `-text` a fresh checkout was clean. On the Mac, after the rule change and
`git restore`, `git status` listed only the attributes file.

**Lesson.** Line-ending rules are a contract between git and everything that
writes to the repository, including bots that commit through an API and never
run git's filters. When a file shows as changed and nobody touched it,
`git ls-files --eol` and `git diff --ignore-cr-at-eol` tell you in seconds
whether only the line endings differ.

---

## EL-021: A health check that could never pass

2026-11-17 · build · fixed in #41

**Symptom.** All three acceptance jobs on #41 failed while starting the stack:
`docker compose up --wait` never saw web-ui become healthy. Locally the
container started and served the dashboard, so nothing looked wrong until
`docker inspect` showed every health check failing with
`wget: can't connect to remote host: Connection refused`.

**Root cause.** The health check called `http://localhost:8080/healthz`. In
Alpine, `localhost` resolves to the IPv6 address `::1` first, and busybox
`wget` does not fall back to `127.0.0.1`. nginx was configured with
`listen 8080;`, which binds IPv4 only. So the check was refused every time,
while every real client, all of them on IPv4, got the dashboard. The Go
services never showed this because their servers listen on IPv4 and IPv6.

**Fix.** The health check calls `127.0.0.1`, the address nginx listens on.
`make up` now waits for every health check the way CI does
(`up --wait`), so a container that never turns healthy fails locally too.

**Verification.** Inside the running container, `wget` to `localhost` was
refused while `127.0.0.1` returned `UP`. After the change, `make up` waits
until web-ui is healthy, and the acceptance jobs pass in CI.

**Lesson.** A container that starts and serves traffic can still fail its
health check, and nothing shows it unless something waits for health. Wait
locally the way CI waits, and point a health check at the address the server
actually binds rather than at a name.

---

## EL-022: Two pull requests, each red because of the other

2026-11-17 · CI · resolved by merge order; the rule is in `tests/acceptance/README.md`

**Symptom.** #42, the Go 1.27.4 fix for GO-2026-6859 in `net/http`, failed
one check, `acceptance/traceability`:
`#40 (See the fleet and steer rollouts in a browser) has no acceptance test in Gherkin and Robot`.
#42 does not touch tests or issues, and every other check on it was green,
govulncheck included. At the same time #41, the dashboard that #40 asks for,
failed govulncheck on GO-2026-6859, which only #42 fixes. Each pull request was
red because of the other one.

**Root cause.** `check_traceability.py` combines two sources. It reads the
customer issues live from the GitHub API, but the exception list from the
commit it runs on. Opening #40 changed the first source for every branch at
once, while its exception line (`40  the dashboard, proved by the Playwright
tests in Phase 3 package 6`) existed only on #41's branch. From that moment on,
every pull request without #41's change failed traceability, whatever it
contained. The red check on #42 said nothing about #42.

**Fix.** Merged #42 first. Its own change was proven by its green govulncheck,
Go tests and acceptance suites, and the one red check was explained by the
issue state, not by the code. #41 then picked up Go 1.27.4 from `main` and
merged with every check green. The rule that prevents a repeat is now in
`tests/acceptance/README.md`: open a customer issue only when the pull request
that adds its tests or its exception line is ready, and merge that one next.

**Verification.** After #42 merged, #41's checks ran on top of it: 16
successful, release-please skipped, traceability and govulncheck among the
green ones.

**Lesson.** A check that reads live state outside the repository is not
hermetic: it can turn red on a commit nobody changed. When a failure has
nothing to do with the diff, look for what changed outside the repository
before touching the code. Reporting issues opened after a pull request's base
commit as warnings would make the check hermetic again; for a new customer
issue every few weeks, a documented order is cheaper than that code.

---

## EL-023: Twenty minutes of fonts and codecs

2026-11-18 · CI · fixed in the pull request that adds this entry

**Symptom.** On #48, `e2e (playwright-java)` took 22m22s and passed. The same
job had taken 2m43s on #43, and #48 changed nothing about how it runs. An
earlier run on #39 had shown the same with the other suite:
`e2e (playwright-ts)` passed after 17m02s. While the job was still running it
looked hung, and the first suspicion was a test blocking forever, since the
Java suite has no per-test time limit. The log disproved that: every test ran
in its usual time.

**Root cause.** A short script listed every gap of 30 seconds or more between
two log lines. All of them were inside `playwright install --with-deps`,
between `Get:` lines from `us-east-2.ec2.archive.ubuntu.com`: 136 s before
`libproxy1v5`, 243 s after `libflite1`, 93 s after `timgm6mb-soundfont`, and
more like them. `--with-deps` installs some 150 packages (fonts, GStreamer,
codecs and the rest that Firefox and WebKit need) from the Ubuntu mirror on
every run. So the job time depended on the mirror's speed at that moment,
which nothing in the repository controls, and it hit whichever suite was
unlucky.

**Fix.** Each suite now runs inside Playwright's own Docker image for the
version it is locked to, `mcr.microsoft.com/playwright` and
`mcr.microsoft.com/playwright/java`, with browsers and system libraries
already in it. `e2e.yml` reads the version from `package-lock.json` and the
pom, so a Playwright bump moves the image with it, and `run.sh` no longer
installs system packages. The suite step has a 10-minute limit and the job 20,
so a slow run fails within minutes instead of half an hour. The images bring
Node.js 26 and JDK 25, so `@types/node` moves to 26, and the pom still
compiles for Java 21.

**Verification.** On this pull request, both e2e jobs run their suite inside
the image, and the suite step's log has no line from an Ubuntu mirror:
`gh run view --job <id> --log | grep 'Run the playwright' | grep -c archive.ubuntu.com`
counts 0 for both jobs.

**Lesson.** Before calling something hung, find where the time went: a
30-line script over the timestamps answered in seconds what guessing would
not have. And a CI step that installs from a public mirror on every run is
only as fast as that mirror today. Bake the dependencies into an image that
is pulled from a registry built for it, and pin that image to the version
the tests need.

---

## EL-024: A wait that never waited

2026-11-18 · build · fixed in #54

**Symptom.** `infra/local/cluster-up.sh` failed on a fresh cluster, both in CI
and on the Mac, with `error: no matching resources found`, a second after the
Gateway was accepted. Run again on the same cluster, the script passed, and the
request through `127.0.0.1:8000` reached Envoy.

**Root cause.** Envoy Gateway creates the Envoy pods for a Gateway a moment
after accepting it. The script was meant to wait for them with
`retry 120 kubectl get pods -l gateway.envoyproxy.io/owning-gateway-name=fleetops`,
then `kubectl wait` for them to be ready. But `kubectl get` exits 0 when no
resource matches: it prints `No resources found` and reports success. So
`retry` returned at once, and `kubectl wait`, which does fail when nothing
matches, ran before the pods existed. On the second run they had been there for
minutes, which is why it passed.

**Fix.** The retry now waits for output instead of an exit code:
`kubectl get pods ... -o name | grep -q .` succeeds only once at least one pod
is listed.

**Verification.** A stand-in `kubectl` that prints nothing for its first two
calls and a pod name on the third: the new check retried until the third call,
10 seconds in. In CI, `cluster.yml` creates a fresh cluster on every run, which
is the case that failed, and passes.

**Lesson.** Know what a command's exit code means before building a wait on it.
"Found nothing" and "failed" are different answers, and `kubectl get` treats
the first as success. A wait that is never exercised in its waiting state, like
one that only ever ran on a cluster that was already up, looks fine until the
first fresh start.

---

## EL-025: A time limit sized on CI

2026-11-18 · build · fixed in #55

**Symptom.** `make cluster-deploy` failed on the Mac with
`error: timed out waiting for the condition on clusters/fleetops-db` after the
script's 5 minutes. The database's first pod was still `PodInitializing`. In
CI, on a fresh cluster, the same script had the database ready 33 seconds
after the install began.

**Root cause.** Nothing was broken; the limit was too short. The pod's events
showed it: `Successfully pulled image "ghcr.io/cloudnative-pg/postgresql:17.12…"
in 6m3.372s`, 153 MB. A GitHub runner downloads that in seconds over a
datacenter connection; the Mac downloads it into the kind node, through
Colima, over a home connection. The operator carried on after the script gave
up, and 15 minutes later the cluster was `Cluster in healthy state`.

**Fix.** The wait is 15 minutes, and the script says beforehand that the first
run on a machine downloads the image. Later runs find the image in the node and
take seconds, as in CI.

**Verification.** Run again on the same cluster, `make cluster-deploy` passed:
`database: PostgreSQL 17.12 … is ready`. The 15 minutes leave more than twice
the measured download time.

**Lesson.** Size a time limit on the slowest place the script runs, measured,
not on the fastest. And before raising a limit, read why it ran out: here the
pod's events gave the download time to the millisecond, which turned a guess
into a number.

---

## EL-026: Two CI jobs lost to one refused download

2026-11-18 · CI · fixed in the pull request that adds this entry; superseded

**Superseded.** The retry loop below never saved a build, and Sonatype advises
against retrying. It was removed when CI moved to building each image once per
commit (EL-027, ADR-0009). The entry stays as it was written.

**Symptom.** On #55, `acceptance (cucumber)` and `acceptance (robot)` failed
after about 35 seconds, while `acceptance (pytest-bdd)` in the same run passed.
#55 changed nothing those jobs use, and a rerun of the failed jobs passed. An
acceptance job on #41 had failed the same way, in the same time, and passed on
a rerun; it was not investigated then.

**Root cause.** The rerun hid the first attempt's log from the default view,
but `gh run view --attempt 1 --log-failed` still had it. Both jobs had failed in
"Start the stack", while Compose built the fleet-api image:

```
#37 [fleet-api build 5/7] RUN ./mvnw -B -v && ./mvnw -B -ntp dependency:go-offline
#37 0.240 wget: Failed to fetch https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.tar.gz
```

The Maven wrapper downloads Maven itself with `wget`, once, with no retry, and
Maven Central refused the request within 0.24 seconds. Central limits
anonymous traffic, and GitHub runners share their addresses (EL-007 was the
same limit, hit by Trivy). The exposure is larger than it looks: every job that
starts the Compose stack, scans an image or builds one builds fleet-api from
scratch, so a single pull request downloads Maven and all of fleet-api's
dependencies seven times. One refusal fails a job.

**Fix.** The image build tries the wrapper's download up to four times, 15, 30
and 45 seconds apart, before it gives up. Maven's own dependency downloads
already retry.

**Verification.** The loop, run with a stand-in `mvnw` that fails twice and
then succeeds, printed two retries and went on to `dependency:go-offline`; with
one that always fails, it stopped after the fourth attempt with exit code 1.
The real refusal cannot be produced on demand, so the evidence that it is gone
is in CI: no acceptance job fails in "Start the stack" again.

**Lesson.** Read the log of a flaky failure before rerunning it, and if a rerun
came first, `--attempt 1` still has the log. "Passed on rerun" is the symptom,
not the diagnosis: this one had happened before and been rerun away. The deeper
fix is downloading less: building each image once per pull request and sharing
it between jobs would turn seven downloads into one. That is recorded as a
follow-up.

## EL-027: The retry that could not help

2026-11-18 · CI · mitigated in #57, fixed by ADR-0009 (#58)

**Symptom.** On #57, the first pull request that installs the services on kind
in CI, `kind cluster` failed in "Install FleetOps" after about 100 seconds,
while building the fleet-api image. The log showed the Dockerfile lines of the
Maven step and `exit code: 1`, and nothing that the step itself printed. On
the Mac, the same script had installed everything a few minutes earlier.

The first fix moved the cluster job's builds into a job that reads the release
workflow's layer cache. Its first run failed the same way, and so did
`image (fleet-api)` in the release workflow, at the same minute. The acceptance
and image scan jobs of that run, which also build fleet-api, passed.

**Root cause.** Three problems, each hiding the next.

- `install-fleetops.sh` built with `docker build --quiet`. When a build fails,
  BuildKit then prints the failing instruction but not its output, so the
  first failure left no error to read.
- Maven Central refused the runner. With the build output visible, the second
  run showed the Maven wrapper's download failing four times, 0.16 seconds
  after each start:

  ```
  #13 0.160 wget: Failed to fetch https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.tar.gz
  #13 0.161 Maven download failed (attempt 1 of 4), retrying in 15 s
  ...
  #13 90.53 wget: Failed to fetch https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.tar.gz
  ```

  Sonatype limits "unusually high-volume traffic" from a "build environment or
  infrastructure provider", and GitHub's runners share their addresses. Its
  advice is explicit: "In most cases, the right answer is not to retry harder.
  More retries usually make the problem worse"
  ([429 FAQ](https://central.sonatype.org/faq/429-error/)). EL-026's retry
  loop goes against that, and it did not save a single build here.
- The cache that should have spared the download did not have the layer. The
  steps before the Maven step came from the cache; the Maven step did not. The
  repository's Actions cache held 10.43 GB in 783 entries, at GitHub's 10 GB
  limit, and 7.6 GB of it belonged to pull requests #35 to #56, all merged and
  never read again. Above the limit GitHub evicts the least recently used
  entries, which is how `main`'s fleet-api layers went.

**Fix.** In #57:

- A new `images` job in `cluster.yml` builds the four images once, in
  parallel, reading the release workflow's cache for each service
  (`cache-from: type=gha,scope=<service>`). It does not write to that cache:
  an amd64-only export would replace the multi-arch one. The images reach the
  cluster job as artifacts, and `install-fleetops.sh` gets `IMAGE_TAG=ci`,
  which loads them into the node instead of building them.
- `install-fleetops.sh` builds with `--progress=plain` into a log file and, if
  a build fails, prints the last 40 lines.
- The caches of the merged pull requests were deleted by hand, from 10.4 GB to
  2.8 GB.

This lowers the odds; it does not remove them. On a cache miss, every job that
builds fleet-api still downloads from Maven Central, up to eight at once. #58
fixes that the way Sonatype asks: each image is built once per pull request and
every job uses it, only `main` writes the build cache, a closed pull request's
caches are deleted, and the retry loop goes.

**Verification.** The script, run with stand-in `docker`, `kind`, `helm`,
`kubectl` and `curl` commands: with `IMAGE_TAG=ci` it loaded the four images
and built none; without it, it built and loaded three; with a build that
fails, it printed the build's error line and stopped. On #57's last run, the
Maven step in `cluster.yml` was still not cached, because no cache had that
layer yet, and that time the download went through. Making a cache hit the
normal case, and a miss cost one download instead of eight, is what ADR-0009
does.

**Lesson.** Three, one per problem. Make output quiet on success, never on
failure: `--quiet` decided what the person debugging would see. A retry only
covers a short outage, and against a rate limit it is part of the problem.
And a cache is an optimisation with its own capacity: nothing here watched its
size until it had been full long enough to evict what mattered.

## EL-028: A cluster living in swap

2026-11-19 · local cluster · fixed in #57

**Symptom.** A second `make cluster-deploy` on the Mac rolled out all three
services and then failed its last check: the dashboard did not answer through
the gateway within 60 seconds. A few minutes later it answered `200`. Envoy's
log showed its configuration stream to the Envoy Gateway controller closing.
The restart counts showed more: within one minute, between 10:04:40 and
10:05:33 UTC, the controller manager, the scheduler, Envoy Gateway, Envoy,
fleet-api and the CloudNativePG operator had all restarted. Since the cluster
was created the evening before, the operator had restarted 30 times, Envoy
Gateway 13, the scheduler 13 and the controller manager 12.

**Root cause.** The Mac has 8 GiB of memory and Colima's VM had been given all
8 GiB. macOS had 16.7 GB of its 17.4 GB of swap in use. Inside the VM nothing
looked wrong: no swap, 5.6 GB available, but 5.2 GB of that was page cache
from image builds and `kind load`. To the VM, page cache is free memory; to
macOS it is memory the VM has used, which macOS has to keep in RAM or in swap.
Every time the VM touched a page macOS had swapped out, the whole VM stopped
until it came back. etcd had logged 1066 "took too long" or "slow fdatasync"
warnings since it started.

The components that restarted together all hold a leader-election lease in
the API server and exit when they cannot renew it in time. A freeze longer
than the lease restarts all of them at once. When that hit during a deploy,
the restarted Envoy Gateway controller had not yet sent Envoy the new web-ui
pod's address, and Envoy kept sending requests to the old one. The power log
showed no sleep or wake at the time, so that cause is ruled out.

**Fix.** Colima's VM gets 5 GiB, `colima start --cpu 4 --memory 5`, which
leaves macOS room for itself. The size is in `infra/local/README.md` and in
the memory budget in `docs/PHASE4.md`.

**Verification.** After the resize, `make cluster-deploy` passed, and in the
30 minutes after the VM restarted, with the deploy in them, nothing restarted
again: every container's last restart was the VM restart itself, at 10:51 to
10:52 UTC. etcd logged 12 slow-request warnings in that time, against 1066
before. The node used 2.4 GiB of the VM's 4.8 GiB, with 2.9 GB available. The
Mac is still tight: it swapped out 85,252 pages, about 1.3 GiB, during the
deploy, so the image builds remain the moment to keep other applications
small.

**Lesson.** A VM sized to the whole machine does not fail with an
out-of-memory error. It gets slow, and the failure shows up somewhere else
entirely: here, as a gateway check on a correct deploy. Free memory inside the
VM says nothing about this; the host's swap does.

## EL-029: A check that only passed once

2026-11-19 · local cluster · fixed in #57

**Symptom.** `make cluster-up` on the running cluster, which already had
FleetOps installed, failed with "the gateway did not answer on
http://127.0.0.1:8000/", while the dashboard on that same address answered.

**Root cause.** The script's last check sent a request for
`dev.fleetops.localhost` and waited for Envoy's `404`, "as it should with no
routes yet". #57 attaches routes for that host, so Envoy answered `200` and
the check waited for a `404` that would never come. The README says the
script can run again at any time. CI did not notice, because CI creates the
cluster once, before anything is installed.

**Fix.** The check asks for `gateway-check.fleetops.localhost`. It is inside
the listener's `*.fleetops.localhost`, so the request still goes through the
`fleetops` Gateway, but no route uses it, so Envoy answers `404` whether or
not FleetOps is installed.

**Verification.** On the Mac, with FleetOps installed, `make cluster-up` ran
twice in a row and both runs ended with "answers 404 for
gateway-check.fleetops.localhost"; the dashboard still answered on
`dev.fleetops.localhost:8000`.

**Lesson.** A check written for the first run ("nothing is there yet") breaks
on the second. For a script that promises it can run again, the check has to
test something that holds in every state it can find.

## EL-030: A cache that held back security fixes

2026-11-19 · CI, security · fixed in #59

**Symptom.** On #59, which builds each image once and scans that build
(ADR-0009), `image scan (web-ui)` failed on the first run and again on a full
rerun: six findings, three of them CRITICAL, among them CVE-2026-121810 in
nginx 1.30.6-r0, fixed in 1.30.7-r0. The image scans on #57, a few hours
earlier, had passed, and that morning an e2e job had installed nginx 1.30.7-r0
while it built web-ui.

**Root cause.** In #59's build of web-ui, the layer that upgrades the packages
came from the cache:

```
#8 [2/5] RUN apk upgrade --no-cache  && apk add --no-cache nginx
#8 CACHED
```

A layer's cache key is its instruction and its inputs, not what Alpine's
package repository holds today. The instruction had not changed, so BuildKit
reused the layer `main`'s cache had from before nginx 1.30.7, and the scan
found exactly what the newer package fixes.

The old setup hid this. The scan job built every image with a plain
`docker build`, without a cache, so it always scanned fresh packages. The
release workflow, though, has always built with the layer cache, so the images
`main` published could carry older packages than the image the scan had
passed. Building once made the scanned image and the tested image the same, and
the difference showed on the first run.

**Fix.** The last stage of every Dockerfile is named `runtime`, and both the
`images` job in `ci.yml` and the release build rebuild it on every run
(`no-cache-filters: runtime`). The build stages, which resolve pinned inputs
(the Maven and Go dependencies), still come from the cache, so there is still
no Maven download.

**Verification.** On #59's next runs, web-ui's upgrade step ran instead of
coming from the cache and installed nginx 1.30.7-r0, and once pebble was
removed (`docs/SECURITY_POLICY.md`) all four image scans passed. This entry
first also said that fleet-api's Maven step stays `CACHED`. It did not, on
either run: see EL-031.

**Lesson.** Cache what is pinned, and rebuild what is meant to change. A layer
whose job is to fetch today's fixes cannot be cached, because nothing in its
cache key knows that the world moved on. And a passing scan only means
something when the scanned image is the one that ships: scanning the published
images is still on the deferred list.

## EL-031: A dependency layer the cache kept losing

2026-11-19 · CI · fixed in the pull request that adds this entry

**Symptom.** The first run of `ci.yml` on `main`, right after #59 merged,
failed in `image (fleet-api)`, and nothing was published:

```
#15 0.145 wget: Failed to fetch https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.tar.gz
```

The step that downloads Maven and fleet-api's dependencies had not come from
the layer cache, so the build went to Maven Central, which refused the runner.
It was the third run in a row with that step not cached. On #59's last run it
missed in the `images` job and missed again in the release build five minutes
later, in the same workflow run, after the `images` job had written its layers
to the cache.

**Root cause.** Not established; what is established is that the design
depended on it. The pattern on #59:

| Runs | `no-cache-filters` | fleet-api's Maven step |
|------|--------------------|------------------------|
| 12:57, 13:06 | no | `CACHED` |
| 13:39, 13:57 | `runtime` | run, downloading from Maven Central |

The base image digest was the same in all four. In the same runs, the Go
images' build stages did come from the cache in the release build. A local
test reproduced the build's shape with a stand-in base image, exported the
cache with and without `--no-cache-filter runtime`, emptied the builder and
imported it again: the `RUN` step was `CACHED` both times. So the filter alone
does not explain it, and the difference is in how the GitHub Actions cache
holds this one large layer. ADR-0009 had made fleet-api's builds safe from
Maven Central only as long as that layer was found. When it was not, every
build downloaded again, and a refusal failed `main`.

**Fix.** fleet-api's image build no longer needs the network for Maven.

- A composite action, `.github/actions/maven-home`, restores `~/.m2` on the
  runner from the Actions cache: the Maven distribution the wrapper uses and
  the dependencies, keyed on the pom and the wrapper's version, with the
  newest older cache as fallback. It then runs the same goals as the image
  build, which download only what the cache lacks.
- The Dockerfile has an empty stage, `maven-home`, which it copies to
  `/root/.m2`. CI passes the runner's `~/.m2` as a build context of that name,
  which replaces the stage, and sets `MAVEN_ARGS=--offline`. The wrapper then
  finds Maven, Maven finds every dependency, and a dependency that is missing
  fails the build by name instead of being fetched.
- On the Mac nothing changes: the stage is empty, `MAVEN_ARGS` is empty, and
  Maven downloads as before.

Maven Central is now contacted by the runner when the pom or the wrapper's
Maven version changes, from a cache that GitHub keys on those files, not on
every layer cache miss. That is what Sonatype asks for: cache artifacts, and
stop downloading from clean environments.

**Verification.** Locally, with the stand-in base image: without the context
the stage is empty and the build runs as it always did; with it,
`/root/.m2` holds the context's files and `MAVEN_ARGS` reaches the `RUN` step
as `--offline`. In CI, the evidence is fleet-api's Maven step running offline,
`[INFO]` lines and no `wget`, in both the `images` job and the release build,
including on a run where the layer cache misses.

**Lesson.** A cache makes a working build faster; it must not be what makes
the build work. When a build only succeeds on a cache hit, every miss is an
outage, and misses happen for reasons you will not always find. Give the build
what it needs in a way that does not depend on a hit, and let the cache only
save time. And a verification line written before the result is a prediction:
EL-030 said the Maven step stayed cached, and it had to be corrected here.
