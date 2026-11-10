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
