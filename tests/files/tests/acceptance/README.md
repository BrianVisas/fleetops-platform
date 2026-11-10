# Acceptance tests

Black-box tests of the whole system, written from the customer's side. They run
against a stack that is already up (Compose locally and in CI, the kind cluster
from Phase 4) and talk to it only over HTTP. Nothing here imports service code.

Every scenario is tagged with the customer issue it proves (`@issue-1`), so each
issue can be traced to the tests that cover it.

## Three suites, one decision

The same scenarios exist in three tools, for a time-boxed comparison:

| Suite | Language | Scenarios written in | HTTP client |
|-------|----------|----------------------|-------------|
| `cucumber/` | Java 21 | `features/*.feature` (Gherkin) | REST Assured |
| `pytest-bdd/` | Python 3 | the same `features/*.feature` | requests |
| `robot/` | Robot Framework | `*.robot` (keyword-driven Given/When/Then) | RequestsLibrary |

Cucumber and pytest-bdd read the same feature files: the Gherkin is the
specification, and each suite only supplies the glue code behind it. Robot
Framework has its own syntax, so its scenarios are written out again.

Once the comparison is done, ADR-0006 records which one stays and why, and the
other two are removed. Running three suites for good would triple the upkeep
for no extra coverage.

### Criteria, fixed before the results

1. **Readability for the customer.** Can someone who does not program read a
   scenario and agree that it says what they asked for?
2. **Glue code.** Lines of code behind the same scenarios.
3. **Failure diagnostics.** When a test fails, does the report show the request
   and the response, or only the assertion?
4. **Run time in CI.** Tool setup plus execution, from the job logs.
5. **Report.** What the HTML report shows without extra plugins.
6. **Fit with the repository.** Toolchains already here (Java and Maven are;
   Python arrives with the ML phase), and who maintains it.
7. **Reuse.** Can the same suite run as the smoke test after each Argo CD sync
   in Phase 4?
8. **Market relevance.** How often the tool appears in German test automation
   and DevOps job ads.

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

`FLEET_API_URL` points the suites at another fleet-api (default
`http://localhost:8080`).

The scenarios do not reset the database. Each one creates its own vehicles with
random VINs and model names, so they can run in any order, against a database
that already holds data, and more than once.
