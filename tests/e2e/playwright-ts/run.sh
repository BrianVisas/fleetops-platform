#!/usr/bin/env sh
# Runs the Playwright Test (TypeScript) suite against a running stack (make up).
# BROWSERS picks the browsers (default: all three). Reports go to reports/:
# html/ (open with `npm run report`), junit.xml, and a trace and screenshot per
# failed test in test-results/.
set -eu
cd "$(dirname "$0")"
BROWSERS="${BROWSERS:-chromium firefox webkit}"

npm ci --no-audit --no-fund

# Downloads the browsers once; a no-op in Playwright's Docker image, which has
# them (CI runs there, see e2e.yml). On a bare Linux machine, the system
# libraries come first, once: npx playwright install-deps
# shellcheck disable=SC2086
npx playwright install $BROWSERS

npm run typecheck

projects=""
for browser in $BROWSERS; do
  projects="$projects --project=$browser"
done

# shellcheck disable=SC2086
npx playwright test $projects "$@"
