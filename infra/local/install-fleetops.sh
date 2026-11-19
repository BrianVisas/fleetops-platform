#!/usr/bin/env sh
# Installs FleetOps into the local cluster's dev environment by hand, until
# Argo CD does it (Phase 4 package 4): the database, then the services, built
# from this checkout and loaded straight into the kind node. Safe to run again.
#
#   SIM=1   also installs vehicle-sim, which sends simulated traffic
set -eu
cd "$(dirname "$0")/../.."
NAMESPACE=fleetops-dev
HOST=dev.fleetops.localhost
CHART=deploy/charts/fleetops-service
GATEWAY_URL=http://127.0.0.1:8000

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

kubectl apply -f infra/local/namespaces.yaml

echo "== database in $NAMESPACE"
# The operator's admission webhook can refuse requests for a few seconds after
# it reports ready, so the install is retried.
retry 60 helm upgrade --install fleetops-postgres deploy/charts/fleetops-postgres \
  --namespace "$NAMESPACE"
# The first install on a machine downloads the PostgreSQL image (about 150 MB)
# into the kind node. In CI that takes seconds; on the Mac it took 6 minutes
# (EL-025), so the limit is sized for the Mac.
echo "waiting for PostgreSQL; the first run on a machine downloads its image first"
kubectl wait cluster/fleetops-db -n "$NAMESPACE" --for=condition=Ready --timeout=900s

# The operator has created the owner's credentials, and PostgreSQL answers.
for key in username password uri jdbc-uri; do
  [ -n "$(kubectl get secret fleetops-db-app -n "$NAMESPACE" -o "jsonpath={.data.$key}")" ] || {
    echo "secret fleetops-db-app has no $key" >&2
    exit 1
  }
done
version=$(kubectl exec -n "$NAMESPACE" fleetops-db-1 -c postgres -- psql -d fleetops -tAc 'show server_version')
echo "database: PostgreSQL $version is ready, credentials in secret fleetops-db-app"

services="fleet-api telemetry-ingest web-ui"
[ "${SIM:-0}" = 1 ] && services="$services vehicle-sim"

# Images from this checkout, so the cluster runs exactly what is on disk. The
# tag names the commit, and "-dirty" plus the time when files have changed, so
# every change is a new tag and Kubernetes rolls the pods. `kind load` copies
# the image into the node: no registry, no download.
#
# IMAGE_TAG=<tag> skips the build and loads fleetops/<service>:<tag>, which must
# already be in Docker. CI uses it: its images are built once, in a separate
# job, from the release workflow's build cache when that has the layers (EL-027).
if [ -n "${IMAGE_TAG:-}" ]; then
  tag=$IMAGE_TAG
  echo "== images $tag, already built"
else
  tag="local-$(git rev-parse --short HEAD)"
  [ -z "$(git status --porcelain)" ] || tag="$tag-dirty-$(date +%s)"
  echo "== images $tag"
fi
build_log=$(mktemp)
for service in $services; do
  if [ -z "${IMAGE_TAG:-}" ]; then
    # The build output goes to a file, and its end is printed if the build
    # fails. `--quiet` printed only the Dockerfile lines, not the error (EL-027).
    docker build --progress=plain --tag "fleetops/$service:$tag" "services/$service" >"$build_log" 2>&1 || {
      tail -n 40 "$build_log" >&2
      echo "building $service failed; the full log is in $build_log" >&2
      exit 1
    }
  fi
  kind load docker-image "fleetops/$service:$tag" --name fleetops >/dev/null
  echo "loaded fleetops/$service:$tag"
done
rm -f "$build_log"

# In order: fleet-api runs the migrations that telemetry-ingest needs, and
# web-ui's nginx looks up fleet-api when it starts.
for service in $services; do
  echo "== $service"
  helm upgrade --install "$service" "$CHART" \
    --namespace "$NAMESPACE" \
    --values "$CHART/values-$service.yaml" \
    --set image.repository="fleetops/$service" \
    --set image.tag="$tag" \
    --set image.pullPolicy=Never \
    --set route.host="$HOST" \
    --wait --timeout 10m >/dev/null
  kubectl rollout status deployment/"$service" -n "$NAMESPACE" --timeout=60s
done

# End to end, through the gateway: the dashboard, and the API behind it.
echo "== checking http://$HOST:8000"
retry 60 sh -c "curl -sf -H 'Host: $HOST' $GATEWAY_URL/ | grep -q '<title>FleetOps</title>'"
retry 60 sh -c "curl -sf -H 'Host: $HOST' '$GATEWAY_URL/api/v1/vehicles?size=1' | grep -q '\"content\"'"
echo "fleetops: dashboard and API answer on http://$HOST:8000"
