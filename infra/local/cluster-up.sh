#!/usr/bin/env sh
# Creates the local kind cluster and its entry point, Envoy Gateway (ADR-0008),
# then proves the path from localhost:8000 to Envoy works.
# Safe to run again: an existing cluster is kept, the rest is re-applied.
set -eu
cd "$(dirname "$0")"
# shellcheck source=/dev/null
. ./versions.env
CLUSTER=fleetops
GATEWAY_URL=http://127.0.0.1:8000/

# Waits up to $1 seconds for the command after it to succeed.
retry() {
  seconds=$1
  shift
  until "$@" >/dev/null 2>&1; do
    seconds=$((seconds - 5))
    [ "$seconds" -gt 0 ] || { echo "timed out: $*" >&2; return 1; }
    sleep 5
  done
}

if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  echo "== cluster $CLUSTER exists, keeping it"
else
  echo "== creating cluster $CLUSTER"
  kind create cluster --config kind-config.yaml --image "$KIND_NODE_IMAGE" --wait 120s
fi
kubectl config use-context "kind-$CLUSTER" >/dev/null

echo "== Envoy Gateway $ENVOY_GATEWAY_VERSION"
helm upgrade --install eg oci://docker.io/envoyproxy/gateway-helm \
  --version "$ENVOY_GATEWAY_VERSION" \
  --namespace envoy-gateway-system --create-namespace \
  --values envoy-gateway-values.yaml \
  --wait --timeout 5m

echo "== the FleetOps gateway"
kubectl apply -f gateway.yaml
kubectl wait gateway/fleetops -n envoy-gateway-system --for=condition=Accepted --timeout=120s
# Envoy Gateway creates the Envoy deployment for the gateway a moment later.
# `kubectl get` exits 0 when nothing matches, so wait for output, not for the
# exit code (EL-024).
retry 120 sh -c 'kubectl get pods -n envoy-gateway-system \
  -l gateway.envoyproxy.io/owning-gateway-name=fleetops -o name | grep -q .'
kubectl wait pods -n envoy-gateway-system \
  -l gateway.envoyproxy.io/owning-gateway-name=fleetops \
  --for=condition=Ready --timeout=180s

# The end-to-end check: a request to localhost:8000 reaches Envoy. With no routes
# attached yet, Envoy answers 404, which is the expected answer here.
echo "== checking $GATEWAY_URL"
retry 60 sh -c "[ \"\$(curl -s -o /dev/null -w '%{http_code}' -H 'Host: dev.fleetops.localhost' $GATEWAY_URL)\" = 404 ]" || {
  echo "the gateway did not answer on $GATEWAY_URL" >&2
  exit 1
}
echo "gateway: $GATEWAY_URL answers 404, as it should with no routes yet"
