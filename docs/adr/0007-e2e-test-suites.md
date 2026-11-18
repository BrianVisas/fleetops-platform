# ADR-0007: Keep the TypeScript and Java Playwright suites

- Status: accepted
- Date: 2026-11-18

## Context

The dashboard (#40) needs browser tests: the acceptance suites prove the API,
not that a person can do the same through the web UI. Phase 3 package 6 (#43)
wrote the same five tests, one per acceptance criterion of #40, in all three
languages Playwright supports. Each suite runs them in Chromium, Firefox and
WebKit against the Compose stack. The criteria were fixed in
`tests/e2e/README.md` before the first CI run; the numbers below are from CI run
44295331411 and the code as merged.

| Criterion | TypeScript (Playwright Test) | Java (JUnit 6) | Python (pytest-playwright) |
|---|---|---|---|
| Code to write, non-blank lines | 280, of which 28 config | 406, of which 97 for the browser lifecycle and keeping traces of failed tests | 213, of which 19 conftest |
| Setup | Node, `npm ci`, `npx playwright install` | JDK and the Maven wrapper; browsers installed by running Playwright's CLI class through `exec:java` | venv, pip, `python -m playwright install` |
| Failure diagnostics | trace, screenshot and a page snapshot (`error-context.md`), linked from the report | trace and screenshot, through a JUnit extension written for this repository | trace and screenshot from two command-line flags |
| Report | HTML report with steps and traces, plus JUnit XML | Surefire XML only | pytest-html list, plus JUnit XML |
| Cross-browser | `projects` in the config: one run, one report | one Maven run per browser, one report folder each | `--browser` repeated; test IDs carry the browser |
| Parallel runs | built in | needs JUnit parallel settings and one Playwright instance per thread | needs pytest-xdist |
| CI, tests only (15 runs) | 15.9 s | about 30 s (about 10 s per browser) | 14.6 s |
| CI, suite step and whole job | 75 s, 2m42 | 79 s, 2m43 | 60 s, 2m18 |
| Fit with the repository | Node used for nothing else | Java and the Maven wrapper already used by fleet-api and Cucumber | Python already used by pytest-bdd and Robot |
| Market relevance (judgement, not counted) | Playwright's primary language; features such as UI mode, codegen, sharding and merged reports arrive here first | the language most German test automation and SDET job ads ask for, often next to Selenium | common in Python, data and automotive teams |

Starting the stack took 63 to 76 seconds in every job, so run time does not
separate the tools. All three suites passed all 15 runs on macOS and in CI, and
each failed exactly the four affected tests against a dashboard broken on
purpose, leaving a trace and a screenshot per failure.

As with ADR-0006, the repository is a practice project: its purpose is to build
and show experience with the tools teams hire for, not to minimise maintenance.

## Options considered

1. **TypeScript only.** The strongest tooling of the three: report, traces,
   browsers and parallel runs all come from the runner, with modest code. What a
   product team would pick for a web front end.
2. **All three, held to the same tests.** The ADR-0006 approach. Three times the
   code for each new dashboard test, and the Python suite adds little that the
   two Python acceptance suites do not already practise.
3. **TypeScript and Java.** The strongest tool, plus the language most asked for
   in the job market this project prepares for, and the one the repository is
   already built around. Java is also where the tool needs the most help: the
   lifecycle, the trace-on-failure extension and the per-browser runs are code
   written here rather than configuration, which is the part worth learning.
4. **Python only.** The least code and nearly the diagnostics of TypeScript, but
   a weaker report and no built-in parallel runs.

## Decision

Option 3. The TypeScript and Java suites stay and gate merges; the Python suite
is removed in the pull request that records this decision. The rules from
package 6 continue for the two suites that remain:

- The same tests, with the same names, in both suites, one test file per issue,
  tagged with it.
- `check_traceability.py` counts an issue as covered by the browser suites only
  when both cover it, and fails when a test exists in one suite but not in the
  other.
- Page objects hold locators and actions, tests hold assertions; locators by
  role and label; web-first assertions only; no retries.

Option 1 remains the recommendation for a team that has to maintain browser
tests for years.

## Consequences

- A new dashboard test means writing it twice. The parity check makes
  forgetting one a CI failure rather than a gap.
- CI runs two e2e jobs per pull request instead of three; they run in parallel,
  so the wall-clock time is one job.
- The Java suite keeps its own lifecycle and trace code, and has no HTML report.
  If its Surefire XML becomes the weak point when reading failures, a report
  plugin is a separate decision.
- Python stays in the repository through the acceptance suites; the e2e Python
  code is in the history of #43.
- Revisit together with ADR-0006 before v1.0.0. If keeping two suites in step
  costs more than it teaches, supersede this ADR with option 1 and delete the
  Java suite in one pull request.
