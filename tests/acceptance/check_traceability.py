"""Checks that every customer issue is proved by acceptance tests.

Reads the customer issues (GitHub API JSON, from `gh api`) and the scenarios in
features/*.feature and robot/*.robot, then fails if:

- a customer issue has no tagged scenario and is not listed in
  coverage-exceptions.txt;
- an issue is listed as an exception but is covered now (the list must shrink);
- a scenario is tagged with an issue that is not a customer issue;
- the Gherkin scenarios and the Robot tests differ (ADR-0006: same scenarios,
  same names, in every suite).

Prints a traceability matrix in Markdown, for the job summary.

Usage: python3 check_traceability.py --issues issues.json
"""
import argparse
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
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


def exceptions(path):
    """Issue number -> reason, from lines like '7  covered by k6 in Phase 5'."""
    result = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if line:
            number, _, reason = line.partition(" ")
            result[int(number)] = reason.strip()
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--issues", required=True, help="JSON from the GitHub issues API")
    args = parser.parse_args()

    issues = {i["number"]: i for i in json.loads(Path(args.issues).read_text(encoding="utf-8"))
              if "pull_request" not in i}
    gherkin = gherkin_scenarios(HERE / "features")
    robot = robot_tests(HERE / "robot")
    allowed = exceptions(HERE / "coverage-exceptions.txt")
    errors = []

    def covering(tests, number):
        return [name for name, tags in tests if number in tags]

    print("## Acceptance test traceability\n")
    print("| Issue | Title | State | Gherkin scenarios | Robot tests | Coverage |")
    print("|---|---|---|---|---|---|")
    for number in sorted(issues):
        issue = issues[number]
        in_gherkin, in_robot = covering(gherkin, number), covering(robot, number)
        if in_gherkin and in_robot:
            status = "covered"
            if number in allowed:
                errors.append(f"#{number} is covered now: remove it from coverage-exceptions.txt")
        elif number in allowed:
            status = f"exception: {allowed[number]}"
        else:
            status = "**MISSING**"
            errors.append(f"#{number} ({issue['title']}) has no acceptance test in "
                          + " and ".join(s for s, t in (("Gherkin", in_gherkin), ("Robot", in_robot)) if not t))
        print(f"| #{number} | {issue['title']} | {issue['state']} "
              f"| {len(in_gherkin)} | {len(in_robot)} | {status} |")

    for suite, tests in (("Gherkin", gherkin), ("Robot", robot)):
        for name, tags in tests:
            if not tags:
                errors.append(f"{suite} scenario '{name}' is not tagged with an issue")
            for number in tags - set(issues):
                errors.append(f"{suite} scenario '{name}' is tagged issue-{number}, which is not a customer issue")

    for name in sorted({n for n, _ in gherkin} ^ {n for n, _ in robot}):
        side = "Gherkin" if name in {n for n, _ in gherkin} else "Robot"
        errors.append(f"'{name}' exists only in {side}: every suite must have the same scenarios (ADR-0006)")

    print(f"\n{len(gherkin)} Gherkin scenarios (run by Cucumber and pytest-bdd), {len(robot)} Robot tests.")
    if errors:
        print("\n### Problems\n")
        for error in errors:
            print(f"- {error}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
