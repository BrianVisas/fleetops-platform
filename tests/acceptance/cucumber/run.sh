#!/usr/bin/env sh
# Runs the Cucumber + REST Assured suite against a running stack (make up).
# Reports go to reports/ (cucumber-report.html, cucumber.json); Surefire's own
# XML results stay in target/surefire-reports/.
set -eu
cd "$(dirname "$0")"
./mvnw -B -ntp test "$@"
