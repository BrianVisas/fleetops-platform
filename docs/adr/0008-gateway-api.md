# ADR-0008: Gateway API with Envoy Gateway as the cluster's entry point

- Status: accepted
- Date: 2026-11-18

## Context

Phase 4 runs FleetOps on Kubernetes, and something has to route requests from
outside the cluster to the dashboard and the APIs. The roadmap says "an ingress
controller", which for most clusters has meant ingress-nginx and the `Ingress`
resource.

ingress-nginx was retired in March 2026: no more releases, no more security
fixes. `Ingress` itself still works, but it is frozen. Its successor is the
Gateway API, which splits the roles: a `GatewayClass` names the implementation,
a `Gateway` is the entry point the platform owns, and each application attaches
`HTTPRoute`s to it from its own namespace.

What this repository needs from the choice:

- it runs on kind on an 8 GiB Mac, and on AKS and STACKIT in Phase 6, with the
  same chart templates;
- it is maintained, open source and free;
- the routes live in each service's chart, next to the service.

## Options considered

1. **Another `Ingress` controller (Traefik, HAProxy, the NGINX Inc. controller).**
   Familiar, and many teams are still on `Ingress`. But it builds the charts on
   an API that is frozen, and moves the migration to later.
2. **Gateway API with Envoy Gateway.** The Envoy project's own implementation,
   built for the Gateway API rather than adapted to it, and conformant to it.
   The controller and one Envoy replica fit in a few hundred MiB. Its Helm chart
   installs the Gateway API CRDs too.
3. **Gateway API with another implementation** (Traefik, NGINX Gateway Fabric,
   Istio, Cilium). All viable. Istio and Cilium bring a service mesh or a new
   network layer that this phase does not need; Traefik and NGINX Gateway
   Fabric would do the same job as option 2 with a different proxy.

## Decision

Option 2. One `Gateway` named `fleetops` in `envoy-gateway-system`, listening on
`*.fleetops.localhost`, with one hostname per environment
(`dev.fleetops.localhost`, `staging.fleetops.localhost`). Only namespaces
labelled `fleetops.io/gateway: "true"` can attach routes to it. The charts ship
an `HTTPRoute` instead of an `Ingress`.

On kind, Envoy runs as a NodePort service on port 30080, which kind maps to
`127.0.0.1:8000` on the Mac. A cloud cluster gets its own `EnvoyProxy` settings
with a `LoadBalancer` service instead; the routes stay the same.

## Consequences

- The charts use an API with a future, and the same `HTTPRoute`s work on kind
  and in both clouds.
- One more component to run and update: the Envoy Gateway chart version is
  pinned in `infra/local/versions.env`.
- The NodePort is fixed with a patch on the Envoy service. That relies on
  Envoy Gateway keeping port 80 as the service port for an HTTP listener on 80;
  `cluster-up.sh` checks the path end to end, so a change there fails loudly.
- People who know `Ingress` need a moment with the new resource names. The
  mapping is direct: `Gateway` plays the controller's listener, `HTTPRoute`
  plays the `Ingress` rule.
