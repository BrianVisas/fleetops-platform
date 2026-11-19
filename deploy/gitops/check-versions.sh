#!/usr/bin/env sh
# The platform's chart versions are pinned twice: in infra/local/versions.env,
# which cluster-up.sh and gitops-bootstrap.sh install from, and in the Argo CD
# applications, which manage those releases afterwards. This fails when the two
# disagree, so a version bump has to change both (cluster.yml runs it).
set -eu
cd "$(dirname "$0")/../.."
# shellcheck source=/dev/null
. infra/local/versions.env
status=0

# The one targetRevision in the file that is not the git branch.
check() {
  file=$1 want=$2
  got=$(grep -E '^ +targetRevision:' "$file" | grep -v ' main$' | awk '{print $2}')
  if [ "$got" = "$want" ]; then
    echo "ok: $file $got"
  else
    echo "MISMATCH: $file has ${got:-nothing}, versions.env has $want" >&2
    status=1
  fi
}

check deploy/gitops/apps/argocd.yaml "$ARGOCD_CHART_VERSION"
check deploy/gitops/apps/envoy-gateway.yaml "$ENVOY_GATEWAY_VERSION"
check deploy/gitops/apps/cloudnative-pg.yaml "$CNPG_CHART_VERSION"
exit $status
