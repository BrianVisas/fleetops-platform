# End-to-end tests

Browser tests of the dashboard (`services/web-ui`), written from the operator's
side. They drive a real browser against a stack that is already up (Compose
locally and in CI) and set up their test data through the API. Nothing here
imports service code.

The acceptance tests in `tests/acceptance` prove the API. These prove that a
person can do the same things through the dashboard: issue #40.

## Three suites, one set of tests

Phase 3 package 6 is a bake-off: the same tests in each language Playwright
supports, so the choice between them rests on evidence (ADR-0007).

| Suite | Language | Runner | Browsers chosen by |
|-------|----------|--------|--------------------|
| `playwright-ts/` | TypeScript | Playwright Test | `projects` in `playwright.config.ts` |
| `playwright-java/` | Java 21 | JUnit 6 | `-Dbrowser`, one Maven run per browser |
| `playwright-python/` | Python 3 | pytest with pytest-playwright | `--browser`, repeated |

Each suite runs every test in Chromium, Firefox and WebKit, headless, and keeps
a trace and a screenshot of every failed test.

The suites follow the same rules, so they can be compared line by line and
checked by `tests/acceptance/check_traceability.py`:

- **One test per acceptance criterion of the issue, with the same name in every
  suite.** Python spells it as a function name
  (`test_pausing_and_resuming_a_rollout`); the check compares names with case
  and punctuation ignored.
- **One test file per issue, tagged with it.** TypeScript puts
  `{ tag: "@issue-40" }` on the `test.describe`, Java `@Tag("issue-40")` on the
  class, Python `pytestmark = pytest.mark.issue(40)` in the module.
- **Page objects hold locators and actions; tests hold the assertions.** One
  page object per view (`VehiclesPage`, `ReleasesPage`, `RolloutPage`), sharing
  the error banner through `DashboardPage`.
- **Locators the way a user finds things:** by role and accessible name
  (`getByRole("region", { name: "Vehicles" })`, `getByRole("button", ...)`) and
  by label (`getByLabel("Model")`). Element ids only for values with no label
  of their own, like the counts in the rollout view.
- **Web-first assertions only.** `toHaveText`, `toBeDisabled` and friends retry
  until the page gets there, so there is no `sleep` anywhere.
- **Test data through the API, never shared.** Each test registers its own
  vehicles under a random model name and publishes its own release, through the
  dashboard's own `/api` proxy. Tests can run in parallel, in any order, against
  a database that already holds data.
- **No retries.** A test that passes on a second try is flaky, and a flaky test
  fails the build until it is fixed.

## Bake-off criteria

Fixed before the first CI run, the way package 1 did it for the acceptance
suites (ADR-0006):

| Criterion | Measured as |
|-----------|-------------|
| Code to write | non-blank lines in tests, page objects and support code |
| Setup | what a newcomer installs and runs before the first test passes |
| Failure diagnostics | what a failed test leaves behind, and how to open it |
| Report | what the suite produces without extra plugins |
| Cross-browser | how the three browsers are selected and reported |
| CI time | the suite step and the whole job, all three browsers |
| Fit with the repository | toolchains already in the repository |
| Market relevance | judgement, recorded but not counted |

The scores and the decision go into ADR-0007.

## Running locally

Start the stack first, from the repository root:

```bash
make up
```

Then run any suite. The first run downloads the browsers (into
`~/Library/Caches/ms-playwright` on macOS); the suites of the same Playwright
version share them.

```bash
tests/e2e/playwright-ts/run.sh
tests/e2e/playwright-java/run.sh
tests/e2e/playwright-python/run.sh
```

`BROWSERS` picks the browsers, and arguments after the script name go to the
runner:

```bash
BROWSERS=chromium tests/e2e/playwright-ts/run.sh --headed
BROWSERS="chromium webkit" tests/e2e/playwright-python/run.sh -k pausing
BROWSERS=firefox tests/e2e/playwright-java/run.sh -Dtest='DashboardTest#apiError'
```

`WEB_UI_URL` points the suites at another dashboard (default
`http://localhost:3000`).

## When a test fails

Each suite writes its reports to `reports/` in its own directory, and CI
uploads them as the workflow artifact `e2e-<suite>`:

| Suite | Report | Per failed test |
|-------|--------|-----------------|
| TypeScript | `reports/html/` (`npm run report`), `reports/junit.xml` | `reports/test-results/<test>/`: `trace.zip`, screenshot, `error-context.md` |
| Java | `reports/<browser>/surefire/` (JUnit XML) | `reports/<browser>/test-results/<method>/`: `trace.zip`, `screenshot.png` |
| Python | `reports/report.html`, `reports/junit.xml` | `reports/test-results/<test>/`: `trace-*.zip`, screenshot |

A trace is the whole test, step by step, with the DOM, network and console at
every step. Open it with `npx playwright show-trace <trace.zip>`, or drop it on
https://trace.playwright.dev, which reads it in the browser without uploading
it.

## Adding a test

1. Add it to the test file of its issue in all three suites, with the same name.
2. New locators go into the page object of their view, in all three suites.
3. A new issue gets a new test file in each suite, tagged with the issue, and
   comes out of `tests/acceptance/coverage-exceptions.txt` in the same pull
   request.
