# Phase 3 — Test automation

Goal: every customer issue is proved by at least one automated acceptance test
that runs against the real system, and the one feature still missing (#6,
auto-pause) is built test-first. Release `v0.3.0`.

## Work packages

| # | Branch | Delivers |
|---|--------|----------|
| 1 | `test/1-acceptance-bake-off` | `tests/acceptance` with the vehicle scenarios (#1) in Cucumber + REST Assured, pytest-bdd and Robot Framework; `acceptance.yml` runs all three against the Compose stack |
| 2 | `test/2-acceptance-decision` | ADR-0006 keeps all three suites, held to the same scenarios; scenarios for #2 (telemetry); a check that every customer issue has a tagged scenario in every suite |
| 3 | `test/3-release-and-rollout` | Scenarios for #3, #4 and #5 |
| 4 | `feat/4-auto-pause` | #6 test-first: the acceptance scenario is written and fails, then the migration, service logic and unit tests make it pass |
| 5 | `feat/5-web-ui` | Small dashboard (vehicle list, rollout status): static HTML and JS behind nginx, which proxies `/api` to fleet-api |
| 6 | `test/6-e2e-bake-off` | The same Playwright scenarios in TypeScript, Java and Python, then ADR-0007 picks one |
| 7 | `docs/7-test-strategy` | `docs/TEST_STRATEGY.md`: the test pyramid, what runs on pull requests, nightly and at release |

## Why the bake-offs

Packages 1 and 6 each build the same small slice in three tools on purpose and
compare them against criteria written down before the results. That is how a
tool choice gets made in a team: a time-boxed spike, evidence, and an ADR,
rather than whatever was installed first.

For the acceptance tests, ADR-0006 keeps all three as a deliberate learning
investment, with a parity check so they stay one specification. A product team
would keep one; the ADR says which, and when to revisit.

## Where the tests sit

| Layer | Lives in | Runs against | Proves |
|-------|----------|--------------|--------|
| Unit | each service | nothing (mocks, fakes) | logic, edge cases |
| Integration | each service (`*IT`, Testcontainers) | a real Postgres | mapping, queries, migrations |
| Acceptance | `tests/acceptance` | the whole stack over HTTP | the customer's acceptance criteria |
| End-to-end | `tests/e2e` | the stack through a browser | the dashboard works for a person |

Acceptance tests import no service code. That keeps them honest about what the
customer sees, and lets the same suite run against the kind cluster after each
Argo CD sync in Phase 4.

## Reports

GitHub Pages is not available for private repositories on the free plan, so test
reports are uploaded as workflow artifacts. Publishing them to Pages is a small
change once the repository is public.

## Done when

- [ ] Every customer issue links to at least one automated acceptance test
- [ ] An open customer issue without a tagged scenario fails CI
- [ ] #6 is implemented, and its scenario failed before the implementation existed
- [ ] Playwright tests run headless in CI, with traces and screenshots on failure
- [ ] ADR-0006 and ADR-0007 record the tool decisions
- [ ] `docs/TEST_STRATEGY.md` is written
- [ ] Tag `v0.3.0` via release-please
