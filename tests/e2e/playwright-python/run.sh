#!/usr/bin/env sh
# Runs the Playwright for Python suite against a running stack (make up), once
# per browser. BROWSERS picks the browsers (default: all three). Reports go to
# reports/: report.html, junit.xml, and a trace and screenshot per failed test
# in test-results/.
set -eu
cd "$(dirname "$0")"
BROWSERS="${BROWSERS:-chromium firefox webkit}"

python3 -m venv .venv
# shellcheck source=/dev/null
. .venv/bin/activate
python3 -m pip install --quiet --disable-pip-version-check -r requirements.txt

# On a CI runner, also install the system libraries the browsers need.
if [ "${CI:-}" = "true" ]; then
  # shellcheck disable=SC2086
  python3 -m playwright install --with-deps $BROWSERS
else
  # shellcheck disable=SC2086
  python3 -m playwright install $BROWSERS
fi

browser_args=""
for browser in $BROWSERS; do
  browser_args="$browser_args --browser $browser"
done

mkdir -p reports
# shellcheck disable=SC2086
python3 -m pytest $browser_args \
  --tracing retain-on-failure \
  --screenshot only-on-failure \
  --output reports/test-results \
  --html=reports/report.html --self-contained-html \
  --junitxml=reports/junit.xml \
  "$@"
