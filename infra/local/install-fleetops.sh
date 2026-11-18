#!/usr/bin/env sh
# Installs FleetOps into the local cluster's dev environment by hand, until
# Argo CD does it (Phase 4 package 4). Today the database; package 3 adds the
# services. Safe to run again.
set -eu
cd "$(dirname "$0")/../.."
NAMESPACE=fleetops-dev

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
kubectl wait cluster/fleetops-db -n "$NAMESPACE" --for=condition=Ready --timeout=300s

# The operator has created the owner's credentials, and PostgreSQL answers.
for key in username password uri jdbc-uri; do
  [ -n "$(kubectl get secret fleetops-db-app -n "$NAMESPACE" -o "jsonpath={.data.$key}")" ] || {
    echo "secret fleetops-db-app has no $key" >&2
    exit 1
  }
done
version=$(kubectl exec -n "$NAMESPACE" fleetops-db-1 -c postgres -- psql -d fleetops -tAc 'show server_version')
echo "database: PostgreSQL $version is ready, credentials in secret fleetops-db-app"
