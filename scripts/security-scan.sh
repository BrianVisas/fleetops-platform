#!/usr/bin/env bash
# Runs the same scans as the security workflow, locally, before pushing.
# Needs docker running; installs nothing permanently.
set -uo pipefail

cd "$(dirname "$0")/.."
failures=0

echo "=== secrets (gitleaks) ==="
if command -v gitleaks > /dev/null; then
  gitleaks detect --redact --no-banner || failures=$((failures + 1))
else
  docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:latest \
    detect --source=/repo --redact --no-banner || failures=$((failures + 1))
fi

echo
echo "=== dependencies and IaC (trivy fs) ==="
docker run --rm -v "$PWD:/repo" -v "$HOME/.cache/trivy:/root/.cache/trivy" \
  aquasec/trivy:latest fs /repo \
  --scanners vuln,secret,misconfig --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 \
  || failures=$((failures + 1))

echo
echo "=== go vulnerabilities (govulncheck) ==="
if ! command -v govulncheck > /dev/null; then
  echo "installing govulncheck..."
  go install golang.org/x/vuln/cmd/govulncheck@latest
fi
for service in telemetry-ingest vehicle-sim; do
  echo "--- $service"
  (cd "services/$service" && "$(go env GOPATH)/bin/govulncheck" ./...) || failures=$((failures + 1))
done

echo
echo "=== images (trivy image) ==="
for service in fleet-api telemetry-ingest vehicle-sim; do
  echo "--- $service"
  docker build -q -t "fleetops/$service:scan" "services/$service" > /dev/null || { failures=$((failures + 1)); continue; }
  docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
    -v "$HOME/.cache/trivy:/root/.cache/trivy" \
    aquasec/trivy:latest image "fleetops/$service:scan" \
    --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 \
    || failures=$((failures + 1))
done

echo
if [ "$failures" -gt 0 ]; then
  echo "$failures scan(s) reported findings"
  exit 1
fi
echo "all scans clean"
