#!/usr/bin/env sh
# Runs the Playwright for Java suite against a running stack (make up), once per
# browser. BROWSERS picks the browsers (default: all three). Reports go to
# reports/<browser>/: surefire/ (JUnit XML), and a trace and screenshot per
# failed test in test-results/.
set -eu
cd "$(dirname "$0")"
BROWSERS="${BROWSERS:-chromium firefox webkit}"

# Playwright for Java has no npx; its CLI is a main class in the dependency.
# On a CI runner, also install the system libraries the browsers need.
install="install $BROWSERS"
if [ "${CI:-}" = "true" ]; then
  install="install --with-deps $BROWSERS"
fi
./mvnw -B -ntp exec:java \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.classpathScope=test \
  -Dexec.args="$install"

# Every browser runs even when an earlier one failed; the exit code reports any failure.
status=0
for browser in $BROWSERS; do
  echo "=== $browser"
  ./mvnw -B -ntp test -Dbrowser="$browser" "$@" || status=1
done
exit $status
