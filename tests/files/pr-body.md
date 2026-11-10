## What

Phase 3, package 1: acceptance tests against the running system, written three ways for a time-boxed comparison.

- `tests/acceptance/features/vehicles.feature`: customer issue #1 as 10 scenarios (registering a vehicle, duplicate and invalid VINs, read, update, delete, the three 404 cases, filtered paging), tagged `@issue-1`.
- `tests/acceptance/cucumber/`: Cucumber 8 + REST Assured 6 on JUnit 6, with the same Maven wrapper as fleet-api.
- `tests/acceptance/pytest-bdd/`: pytest-bdd + requests. It reads the **same** feature file as Cucumber.
- `tests/acceptance/robot/`: Robot Framework + RequestsLibrary, with the same scenarios in Robot's own syntax.
- `.github/workflows/acceptance.yml`: one job per suite. Each job starts the Compose stack built from the PR (`up --wait` on the health checks), runs its suite, uploads the report, prints the service logs on failure and tears the stack down. It runs on every PR and push to `main`, plus nightly. There's no path filter, so it always reports.
- `docs/PHASE3.md`: the phase plan.
- `docs/engineering-log.md`: EL-020 (the `mvnw.cmd` line endings) and a follow-up to EL-017 (the squash body still caused duplicate changelog lines, so it's now blank).

## Why

The roadmap's done-when line for Phase 3 is that every customer issue links to an automated acceptance test. These tests check the system from outside, over HTTP, and import no service code. The same suite can run as the smoke test against the kind cluster in Phase 4.

Three tools for one job is temporary. The criteria are written down in `tests/acceptance/README.md` before the results. ADR-0006, in the next PR, picks one, and the other two are removed.

## How it was verified

- [ ] Locally against `make up`: all three suites pass
- [ ] Locally with fleet-api stopped: the suite fails (proves it can fail)
- [ ] CI: `acceptance (cucumber)`, `acceptance (pytest-bdd)` and `acceptance (robot)` are green, and each uploads a report

Before this PR, the pytest-bdd and Robot suites were run against a stand-in for the vehicle endpoints. Both passed, both failed exactly one scenario when duplicate VINs were wrongly accepted, and every scenario failed with no server. The Java suite was type-checked against the library signatures. Its first real run is the local run above.

## Not in this PR

- Dependabot entries for the suites. They come with the ADR, for the one that stays.
- Scenarios for #2 to #6.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01CAdJZ9BYGkWZrGLzHu9ck3
