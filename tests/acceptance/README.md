# Acceptance tests

Black-box tests of the whole system, written from the customer's side. They run
against a stack that is already up (Compose locally and in CI, the kind cluster
from Phase 4) and talk to it only over HTTP. Nothing here imports service code.

Every scenario is tagged with the customer issue it proves (`@issue-1`), so each
issue can be traced to the tests that cover it.

## Three suites, one specification

The same scenarios run in three tools (ADR-0006):

| Suite | Language | Scenarios written in | HTTP client |
|-------|----------|----------------------|-------------|
| `cucumber/` | Java 21 | `features/*.feature` (Gherkin) | REST Assured |
| `pytest-bdd/` | Python 3 | the same `features/*.feature` | requests |
| `robot/` | Robot Framework | `*.robot` (keyword-driven Given/When/Then) | RequestsLibrary |

Cucumber and pytest-bdd read the same feature files: the Gherkin is the
specification, and each suite only supplies the glue code behind it. Robot
Framework has its own syntax, so its tests repeat the scenarios with the same
names and tags.

The three were scored against criteria fixed before the results; the scores,
the decision and the rules that keep the suites in step are in ADR-0006.

## Traceability

`check_traceability.py` runs in CI next to the suites. It reads the customer
issues from GitHub and the scenarios from `features/` and `robot/`, prints a
matrix of issue against scenarios to the job summary, and fails when:

- a customer issue has no scenario and is not listed in
  `coverage-exceptions.txt` with a reason;
- an issue in that list is covered now (the list only ever shrinks);
- a scenario is tagged with an issue that is not a customer issue;
- a scenario exists in the Gherkin files but not in Robot, or the other way round.

To run it locally:

```bash
gh api "repos/BrianVisas/fleetops-platform/issues?labels=customer&state=all" > /tmp/issues.json
python3 tests/acceptance/check_traceability.py --issues /tmp/issues.json
```

## Adding a scenario

1. Write it in Gherkin, in the feature file for its issue, under that issue's
   `@issue-N` tag.
2. Add any new steps to the Cucumber step classes and to the pytest-bdd test
   module for that feature. Steps used by several features live in
   `VehicleSteps` (Cucumber, shared through `World`) and `conftest.py`
   (pytest-bdd).
3. Add a Robot test with exactly the same name to the matching `.robot` file.
4. If the issue was in `coverage-exceptions.txt`, remove it.

A failing step prints the full request and response in all three reports.

## Running locally

Start the stack first, from the repository root:

```bash
make up
```

Then run any suite. Each one writes its reports to `reports/` in its own
directory:

```bash
tests/acceptance/cucumber/run.sh
tests/acceptance/pytest-bdd/run.sh
tests/acceptance/robot/run.sh
```

`FLEET_API_URL` and `TELEMETRY_URL` point the suites at another fleet-api and
telemetry-ingest (defaults `http://localhost:8080` and `http://localhost:8081`).

The scenarios do not reset the database. Each one creates its own vehicles with
random VINs and model names, so they can run in any order, against a database
that already holds data, and more than once.
