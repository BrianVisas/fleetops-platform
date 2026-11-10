#!/usr/bin/env sh
# Runs the pytest-bdd suite against a running stack (make up).
# Reports go to reports/.
set -eu
cd "$(dirname "$0")"
python3 -m venv .venv
# shellcheck source=/dev/null
. .venv/bin/activate
python3 -m pip install --quiet --disable-pip-version-check -r requirements.txt
mkdir -p reports
python3 -m pytest \
  --html=reports/report.html --self-contained-html \
  --cucumberjson=reports/cucumber.json \
  --junitxml=reports/junit.xml \
  "$@"
