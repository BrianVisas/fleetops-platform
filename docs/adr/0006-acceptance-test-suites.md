# ADR-0006: Keep three acceptance test suites, held to the same scenarios

- Status: accepted
- Date: 2026-11-10

## Context

Phase 3 needs black-box acceptance tests that prove each customer issue against
the running system. Phase 3 package 1 (#33) built the same ten scenarios for
issue #1 in three tools, scored against criteria written down before any
results existed (`tests/acceptance/README.md`):

| Criterion | Cucumber + REST Assured | pytest-bdd | Robot Framework |
|---|---|---|---|
| Customer can read the spec | plain `.feature` file | the same file | readable, but shares a file with settings and keywords |
| Glue code | 222 lines of Java | 127 lines of Python | 119 lines, plus the scenarios written again |
| Failure diagnostics | request and response only for REST Assured checks | assertion messages carry the response | every keyword, request and response in `log.html` |
| CI time, suite step | 11 s | 10 s + 6 s Python setup | 4 s |
| Report | steps per scenario | basic list | report plus drill-down log |
| Fit with the repository | Java, Maven wrapper and Dependabot already here | Python not used elsewhere until Phase 9 | as pytest-bdd |
| Reuse as the Phase 4 smoke test | equal | equal | equal |
| Market relevance (judgement, not counted) | strongest in German test automation and SDET ads | common in Python teams | strong in automotive and telecom |

Starting the stack took 63 to 76 seconds in every job, so run time does not
separate the tools. Every suite ran all ten scenarios locally and in CI, and
failed with fleet-api stopped.

The repository is a practice project: its purpose is to build and show working
experience with the tools teams hire for, not to minimise maintenance.

## Options considered

1. **Keep Cucumber + REST Assured only.** The best fit with the repository and
   the strongest on readability and market relevance, at the cost of the
   weakest failure diagnostics. This is what a product team would do: one
   suite, one set of glue code to maintain.
2. **Keep Robot Framework only.** The best reports and diagnostics with the
   least code, but adds a Python toolchain now and repeats the scenarios in a
   second syntax.
3. **Keep all three, held to the same scenarios.** Every scenario exists in
   every suite, so each tool is practised on the whole product rather than one
   slice of it. Costs roughly three times the glue code for each new scenario.

## Decision

Option 3. All three suites stay, each gating merges, with these rules so that
three suites stay one specification and do not drift into three:

- Scenarios are written once, in Gherkin, in `features/`. Cucumber and
  pytest-bdd run those files directly.
- The Robot tests carry the same names as the Gherkin scenarios, and the same
  `issue-N` tags.
- `check_traceability.py` runs on every pull request and fails when the
  Gherkin scenarios and the Robot tests differ, when a customer issue has no
  scenario and no recorded exception, or when a scenario names an issue that
  does not exist.
- Each suite reports the full request and response of a failing step: Robot by
  default, Cucumber through an `@After` hook that attaches the scenario's HTTP
  log to the report, pytest-bdd through a step-error hook.

Option 1 remains the recommendation for a team that has to maintain the suites
for years. The choice here is a deliberate learning investment, recorded as one.

## Consequences

- A new scenario means a Gherkin scenario, step code in Java and in Python, and
  a Robot test. The parity check makes forgetting one of them a CI failure
  rather than a silent gap.
- CI runs three acceptance jobs per pull request. Each one starts its own stack,
  about a minute of runner time each; they run in parallel, so the wall-clock
  cost is one job.
- Python enters the CI toolchain in Phase 3 rather than Phase 9, with
  Dependabot updates for both Python suites.
- The comparison stays live: the same failure can be read in three reports, which
  is the most direct way to learn what each tool does well.
- Revisit before v1.0.0. If keeping the suites in step costs more than it
  teaches, supersede this ADR with option 1, and delete the other two suites
  in one pull request.
