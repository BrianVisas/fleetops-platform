#!/usr/bin/env sh
# Runs the Robot Framework suite against a running stack (make up).
# Reports go to reports/ (report.html, log.html, output.xml, xunit.xml).
set -eu
cd "$(dirname "$0")"
python3 -m venv .venv
# shellcheck source=/dev/null
. .venv/bin/activate
python3 -m pip install --quiet --disable-pip-version-check -r requirements.txt
robot --outputdir reports --xunit xunit.xml "$@" vehicles.robot
