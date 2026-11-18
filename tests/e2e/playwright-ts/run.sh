#!/usr/bin/env sh
# Runs the Playwright Test (TypeScript) suite against a running stack (make up).
# BROWSERS picks the browsers (default: all three). Reports go to reports/:
# html/ (open with `npm run report`), junit.xml, and a trace and screenshot per
# failed test in test-results/.
set -eu
cd "$(dirname "$0")"
BROWSERS="${BROWSERS:-chromium firefox webkit}"

npm ci --no-audit --no-fund

# On a CI runner, also install the system libraries the browsers need.
if [ "${CI:-}" = "true" ]; then
  # shellcheck disable=SC2086
  npx playwright install --with-deps $BROWSERS
else
  # shellcheck disable=SC2086
  npx playwright install $BROWSERS
fi

npm run typecheck

projects=""
for browser in $BROWSERS; do
  projects="$projects --project=$browser"
done

# shellcheck disable=SC2086
npx playwright test $projects "$@"
