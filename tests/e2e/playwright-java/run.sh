#!/usr/bin/env sh
# Runs the Playwright for Java suite against a running stack (make up), once per
# browser. BROWSERS picks the browsers (default: all three). Reports go to
# reports/<browser>/: surefire/ (JUnit XML), and a trace and screenshot per
# failed test in test-results/.
set -eu
cd "$(dirname "$0")"
BROWSERS="${BROWSERS:-chromium firefox webkit}"

# Playwright for Java has no npx; its CLI is a main class in the dependency.
# It downloads the browsers once, and is a no-op in Playwright's Docker image,
# which has them (CI runs there, see e2e.yml). On a bare Linux machine, the
# system libraries come first, once: the same command with "install-deps".
./mvnw -B -ntp exec:java \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.classpathScope=test \
  -Dexec.args="install $BROWSERS"

# Every browser runs even when an earlier one failed; the exit code reports any failure.
status=0
for browser in $BROWSERS; do
  echo "=== $browser"
  ./mvnw -B -ntp test -Dbrowser="$browser" "$@" || status=1
done
exit $status
