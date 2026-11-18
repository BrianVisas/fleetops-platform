"""Checks that every customer issue is proved by automated tests.

Reads the customer issues (GitHub API JSON, from `gh api`) and the tests that
name them:

- acceptance scenarios in features/*.feature and robot/*.robot (ADR-0006);
- browser tests in the Playwright suites under tests/e2e/ (ADR-0007).

An issue is covered when the acceptance suites cover it (Gherkin and Robot), or
when the browser suites do (every Playwright suite). The check fails if:

- a customer issue is not covered and is not listed in coverage-exceptions.txt;
- an issue is listed as an exception but is covered now (the list must shrink);
- a test is tagged with an issue that is not a customer issue, or with none;
- the Gherkin scenarios and the Robot tests differ (ADR-0006: same scenarios,
  same names, in every suite);
- the Playwright suites differ in the same way.

Prints a traceability matrix in Markdown, for the job summary.

Usage: python3 check_traceability.py --issues issues.json
"""
import argparse
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
E2E = HERE.parent / "e2e"
TAG = re.compile(r"@?issue-(\d+)$")


def gherkin_scenarios(directory):
    """(scenario name, issue numbers) for every scenario in the feature files."""
    found = []
    for path in sorted(directory.glob("*.feature")):
        feature_tags, pending = set(), set()
        for raw in path.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if line.startswith("@"):
                pending |= {int(m.group(1)) for t in line.split() if (m := TAG.match(t))}
            elif line.startswith("Feature:"):
                feature_tags, pending = pending, set()
            elif m := re.match(r"Scenario(?: Outline)?:\s*(.+)", line):
                found.append((m.group(1).strip(), feature_tags | pending))
                pending = set()
    return found


def robot_tests(directory):
    """(test name, issue numbers) for every test case in the .robot files."""
    found = []
    for path in sorted(directory.glob("*.robot")):
        section, suite_tags, current = None, set(), None
        for raw in path.read_text(encoding="utf-8").splitlines():
            if raw.startswith("***"):
                section = raw.strip("* ").lower()
                continue
            cells = [c for c in re.split(r"\s{2,}|\t", raw.strip()) if c]
            if not cells or cells[0].startswith("#"):
                continue
            if section == "settings" and cells[0].lower() == "test tags":
                suite_tags = {int(m.group(1)) for c in cells[1:] if (m := TAG.match(c))}
            elif section == "test cases":
                if not raw[0].isspace():
                    current = (raw.strip(), set(suite_tags))
                    found.append(current)
                elif current and cells[0].lower() == "[tags]":
                    current[1].update(int(m.group(1)) for c in cells[1:] if (m := TAG.match(c)))
    return found


# The Playwright suites keep one test file per issue, with the issue tag on the
# file's test group, the way a feature file carries its tag:
#
#   TypeScript  test.describe("...", { tag: "@issue-40" }, ...)   test("<name>", ...)
#   Java        @Tag("issue-40") on the class                      @DisplayName("<name>")
PLAYWRIGHT = {
    "TypeScript": ("playwright-ts/tests", "*.spec.ts",
                   r"""tag:\s*\[?[^\]}]*["']@issue-(\d+)["']""", r"""^\s*test\(\s*["'](.+?)["']\s*,"""),
    "Java": ("playwright-java/src/test/java", "**/*Test.java",
             r"""@Tag\("issue-(\d+)"\)""", r"""@DisplayName\("(.+?)"\)"""),
}


def playwright_tests(language):
    """(normalised test name, issue numbers) for every test of one Playwright suite."""
    directory, pattern, tag, name = PLAYWRIGHT[language]
    found = []
    for path in sorted((E2E / directory).glob(pattern)):
        text = path.read_text(encoding="utf-8")
        issues = {int(n) for n in re.findall(tag, text)}
        for test in re.findall(name, text, re.MULTILINE):
            found.append((normalise(test), issues))
    return found


def normalise(name):
    """'an API error is shown, 20 per page' -> 'an api error is shown 20 per page'."""
    return " ".join(re.findall(r"[a-z0-9]+", name.lower()))


def exceptions(path):
    """Issue number -> reason, from lines like '7  covered by k6 in Phase 5'."""
    result = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if line:
            number, _, reason = line.partition(" ")
            result[int(number)] = reason.strip()
    return result


def parity(errors, suites, rule):
    """Every suite in the group has exactly the same test names."""
    names = {suite: {n for n, _ in tests} for suite, tests in suites.items()}
    every = set().union(*names.values())
    for name in sorted(every):
        missing = [suite for suite, have in names.items() if name not in have]
        if missing:
            errors.append(f"'{name}' is missing in {' and '.join(missing)}: {rule}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--issues", required=True, help="JSON from the GitHub issues API")
    args = parser.parse_args()

    issues = {i["number"]: i for i in json.loads(Path(args.issues).read_text(encoding="utf-8"))
              if "pull_request" not in i}
    acceptance = {"Gherkin": gherkin_scenarios(HERE / "features"), "Robot": robot_tests(HERE / "robot")}
    e2e = {f"Playwright {language}": playwright_tests(language) for language in PLAYWRIGHT}
    allowed = exceptions(HERE / "coverage-exceptions.txt")
    errors = []

    def covering(tests, number):
        return [name for name, tags in tests if number in tags]

    columns = {**acceptance, **e2e}
    print("## Test traceability\n")
    print("| Issue | Title | State | " + " | ".join(columns) + " | Coverage |")
    print("|---|---|---|" + "---|" * len(columns) + "---|")
    for number in sorted(issues):
        issue = issues[number]
        counts = {suite: len(covering(tests, number)) for suite, tests in columns.items()}
        by_acceptance = all(counts[s] for s in acceptance)
        by_e2e = all(counts[s] for s in e2e)
        if by_acceptance or by_e2e:
            status = "covered"
            if number in allowed:
                errors.append(f"#{number} is covered now: remove it from coverage-exceptions.txt")
        elif number in allowed:
            status = f"exception: {allowed[number]}"
        else:
            status = "**MISSING**"
            partial = [s for s, n in counts.items() if n]
            group = e2e if any(s in e2e for s in partial) else acceptance
            missing = [s for s in group if not counts[s]]
            errors.append(f"#{number} ({issue['title']}) has no test in " + " and ".join(missing))
        print(f"| #{number} | {issue['title']} | {issue['state']} | "
              + " | ".join(str(counts[s]) for s in columns) + f" | {status} |")

    for suite, tests in columns.items():
        for name, tags in tests:
            if not tags:
                errors.append(f"{suite} test '{name}' is not tagged with an issue")
            for number in tags - set(issues):
                errors.append(f"{suite} test '{name}' is tagged issue-{number}, which is not a customer issue")

    parity(errors, acceptance, "every acceptance suite must have the same scenarios (ADR-0006)")
    parity(errors, e2e, "every Playwright suite must have the same tests (ADR-0007)")

    print(f"\n{len(acceptance['Gherkin'])} Gherkin scenarios (run by Cucumber and pytest-bdd), "
          f"{len(acceptance['Robot'])} Robot tests, "
          + ", ".join(f"{len(tests)} {suite} tests" for suite, tests in e2e.items()) + ".")
    if errors:
        print("\n### Problems\n")
        for error in errors:
            print(f"- {error}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
