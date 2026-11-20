#!/usr/bin/env sh
# Hands the local cluster to git, once per cluster (ADR-0010): installs Argo CD,
# gives it read access to this repository and the cluster read access to GHCR,
# and applies the root application. From then on Argo CD runs every component
# from deploy/gitops, and changes arrive by pull request.
#
# Run after `make cluster-up`, on a cluster without a manual `make
# cluster-deploy`. Safe to run again.
#
# The two credentials live outside the repository and only reach the cluster:
#   ~/.config/fleetops/argocd-deploy-key   created here, added to the
#                                         repository as a read-only deploy key
#   ~/.config/fleetops/ghcr-read-token     a classic token with read:packages
#                                         only, created by you (see below)
set -eu
cd "$(dirname "$0")/../.."
# shellcheck source=/dev/null
. infra/local/versions.env
REPO=BrianVisas/fleetops-platform
REPO_URL="git@github.com:$REPO.git"
SECRETS="${FLEETOPS_SECRETS_DIR:-$HOME/.config/fleetops}"
DEPLOY_KEY="$SECRETS/argocd-deploy-key"
GHCR_TOKEN="$SECRETS/ghcr-read-token"
GATEWAY_URL=http://127.0.0.1:8000

fail() { echo "gitops-bootstrap: $*" >&2; exit 1; }

sha256() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1
}

retry() {
  seconds=$1
  shift
  until "$@" >/dev/null 2>&1; do
    seconds=$((seconds - 5))
    [ "$seconds" -gt 0 ] || { echo "timed out: $*" >&2; return 1; }
    sleep 5
  done
}

[ "$(kubectl config current-context)" = kind-fleetops ] || fail "kubectl does not point at the kind cluster: make cluster-up"
gh auth status >/dev/null 2>&1 || fail "gh is not logged in: gh auth login"
if kubectl get deployment fleet-api -n fleetops-dev >/dev/null 2>&1 \
  && ! kubectl get crd applications.argoproj.io >/dev/null 2>&1; then
  fail "fleetops-dev was installed by hand (make cluster-deploy); start from a fresh cluster: make cluster-down cluster-up"
fi

echo "== credentials in $SECRETS"
mkdir -p "$SECRETS"
chmod 700 "$SECRETS"

# A deploy key reads one repository and nothing else. gh adds it read-only
# unless asked for write access.
[ -f "$DEPLOY_KEY" ] || ssh-keygen -q -t ed25519 -N '' -C "argocd, local kind cluster" -f "$DEPLOY_KEY"
public_key=$(cut -d' ' -f1,2 "$DEPLOY_KEY.pub")
if gh repo deploy-key list -R "$REPO" --json key -q '.[].key' | grep -qxF "$public_key"; then
  echo "deploy key: already on $REPO"
else
  gh repo deploy-key add "$DEPLOY_KEY.pub" -R "$REPO" --title "argocd (local kind cluster)"
fi

# GHCR has no fine-grained tokens, so the token is a classic one, and the only
# scope it may have is read:packages. Checked, not trusted.
save_hint="Create a classic token with only read:packages at
  https://github.com/settings/tokens/new?scopes=read:packages&description=fleetops-ghcr-pull
copy it, and save it at a hidden prompt:
  (umask 077; printf 'GHCR token: '; read -rs t; printf '%s' \"\$t\" > $GHCR_TOKEN; echo)"
[ -s "$GHCR_TOKEN" ] || fail "no GHCR token. $save_hint"
chmod 600 "$GHCR_TOKEN"
# A classic token is one line of 40 characters starting with ghp_. Anything
# else is a wrong paste, such as the command that was meant to save it (EL-032),
# and fails here by name instead of later inside curl.
if ! grep -qE '^ghp_[A-Za-z0-9]{36}$' "$GHCR_TOKEN" || [ "$(wc -l < "$GHCR_TOKEN" | tr -d ' ')" -gt 1 ]; then
  fail "$GHCR_TOKEN does not hold a classic token (one line, ghp_ and 36 letters or digits). $save_hint"
fi
scopes=$(curl -sS -o /dev/null -D - -H "Authorization: Bearer $(cat "$GHCR_TOKEN")" https://api.github.com/user \
  | tr -d '\r' | sed -n 's/^[Xx]-[Oo][Aa]uth-[Ss]copes: //p')
[ "$scopes" = "read:packages" ] || fail "the GHCR token must have exactly the read:packages scope, it has: '${scopes:-none}'"
owner=$(gh api user -q .login)
curl -sSf -o /dev/null -H "Authorization: Bearer $(cat "$GHCR_TOKEN")" \
  "https://api.github.com/users/$owner/packages/container/fleetops-fleet-api" \
  || fail "the GHCR token cannot read the fleetops-fleet-api package"
echo "ghcr token: read:packages only, and it can read the images"

echo "== Argo CD $ARGOCD_VERSION (chart $ARGOCD_CHART_VERSION)"
chart="infra/local/.cache/argo-cd-$ARGOCD_CHART_VERSION.tgz"
if [ ! -f "$chart" ] || [ "$(sha256 "$chart")" != "$ARGOCD_CHART_SHA256" ]; then
  mkdir -p infra/local/.cache
  curl -sSfL -o "$chart" \
    "https://github.com/argoproj/argo-helm/releases/download/argo-cd-$ARGOCD_CHART_VERSION/argo-cd-$ARGOCD_CHART_VERSION.tgz"
fi
[ "$(sha256 "$chart")" = "$ARGOCD_CHART_SHA256" ] || fail "the Argo CD chart does not match its checksum in versions.env"
kubectl apply -f infra/local/namespaces.yaml
helm upgrade --install argocd "$chart" \
  --namespace argocd \
  --values deploy/gitops/argocd/values.yaml \
  --wait --timeout 10m >/dev/null
echo "argo cd: installed"

echo "== secrets, in the cluster only"
# --dry-run=client builds the secret locally and apply stores it, so running
# this again updates it instead of failing because it exists.
kubectl create secret generic repo-fleetops-platform -n argocd \
  --from-literal=type=git \
  --from-literal=url="$REPO_URL" \
  --from-file=sshPrivateKey="$DEPLOY_KEY" \
  --dry-run=client -o yaml \
  | kubectl label --local -f - argocd.argoproj.io/secret-type=repository -o yaml \
  | kubectl apply -f - >/dev/null
echo "repository: $REPO_URL with the deploy key"
# One pull secret per FleetOps namespace; staging gets its own in Phase 6.
kubectl create secret docker-registry ghcr-pull -n fleetops-dev \
  --docker-server=ghcr.io \
  --docker-username="$owner" \
  --docker-password="$(cat "$GHCR_TOKEN")" \
  --dry-run=client -o yaml \
  | kubectl apply -f - >/dev/null
echo "ghcr-pull: in fleetops-dev"

echo "== the root application"
kubectl apply -f deploy/gitops/root.yaml >/dev/null

# Every application synced and healthy: root, argocd, envoy-gateway,
# cloudnative-pg, platform-config, and dev's database and four services. The
# first sync pulls the images from GHCR, so it takes a few minutes.
apps_ready() {
  kubectl get applications -n argocd \
    -o jsonpath='{range .items[*]}{.metadata.name} {.status.sync.status} {.status.health.status}{"\n"}{end}' \
    > "${TMPDIR:-/tmp}/fleetops-apps.txt"
  [ "$(wc -l < "${TMPDIR:-/tmp}/fleetops-apps.txt")" -ge 10 ] \
    && ! grep -qvE ' Synced Healthy$' "${TMPDIR:-/tmp}/fleetops-apps.txt"
}
echo "waiting for 10 applications to be synced and healthy (up to 20 minutes)"
waited=0
until apps_ready; do
  waited=$((waited + 30))
  [ "$waited" -le 1200 ] || {
    cat "${TMPDIR:-/tmp}/fleetops-apps.txt" >&2
    fail "not every application became synced and healthy; see above and the Argo CD UI"
  }
  sleep 30
  printf '  %s min: %s of %s ready\n' "$((waited / 60))" \
    "$(grep -cE ' Synced Healthy$' "${TMPDIR:-/tmp}/fleetops-apps.txt" || true)" \
    "$(wc -l < "${TMPDIR:-/tmp}/fleetops-apps.txt" | tr -d ' ')"
done
cat "${TMPDIR:-/tmp}/fleetops-apps.txt"

echo "== checking dev and Argo CD through the gateway"
host=dev.fleetops.localhost
retry 120 sh -c "curl -sf -H 'Host: $host' $GATEWAY_URL/ | grep -q '<title>FleetOps</title>'"
retry 120 sh -c "curl -sf -H 'Host: $host' '$GATEWAY_URL/api/v1/vehicles?size=1' | grep -q '\"content\"'"
echo "fleetops dev: dashboard and API answer on http://$host:8000"
retry 60 sh -c "curl -sf -H 'Host: argocd.fleetops.localhost' $GATEWAY_URL/ | grep -q 'Argo CD'"
echo "argo cd: http://argocd.fleetops.localhost:8000, user admin, password from"
echo "  kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d; echo"
