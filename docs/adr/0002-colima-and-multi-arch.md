# ADR-0002: Colima on Apple Silicon, multi-arch images built in CI

- Status: accepted
- Date: 2026-09-17 (recorded 2026-11-09; written in Phase 0 but never committed)

## Context

Development happens on a MacBook Pro with an M1 processor (arm64). GitHub's hosted
runners and the planned cloud clusters (AKS for staging, SKE for production) run
amd64. Containers need a Linux VM on macOS, and every image has to run on both
architectures without anyone building them by hand.

## Options considered

**Container runtime on the Mac**

1. **Docker Desktop.** The most familiar option, with a GUI. Its licence is free
   for personal use but not for larger companies, so habits built on it may not
   carry over to an employer, and it runs a heavier VM than a CLI-only workflow
   needs.
2. **Colima.** Open source and CLI-driven, using the macOS Virtualization
   framework. It exposes a standard Docker socket and works with the normal
   `docker` CLI, buildx and Compose. Its socket is not at the default path, which
   some tools have to be told about.
3. **Podman.** Daemonless and rootless, but less compatible with tooling that
   expects a Docker socket, such as Testcontainers' default configuration.

**Architectures**

1. **Build only for the machine you are on.** Images built on the Mac would be
   arm64 and fail on amd64 clusters.
2. **Build both architectures locally.** Slow on a laptop, and dependent on local
   emulation set-up.
3. **Build natively on the Mac, build both architectures in CI.** The Mac builds
   arm64 for local work; CI builds and publishes `linux/amd64` and `linux/arm64`
   for everything that leaves the laptop.

## Decision

Colima as the container runtime (`colima start`, Docker runtime, Virtualization
framework), and multi-arch images built only in CI. Images built on the Mac are for
local use and are never pushed. ADR-0005 specifies how CI builds and publishes
them.

## Consequences

- Testcontainers needs two settings on this machine:
  `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` for the client, and
  `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` for its Ryuk
  container, which mounts the socket path as it is inside the VM (EL-003).
- Docker Desktop is also installed on this Mac, as the `desktop-linux` context.
  Exporting `DOCKER_HOST` makes the Colima socket win explicitly, so nothing
  depends on which context happens to be active.
- Architecture problems surface in CI, not on the laptop. The pull-request build
  of both platforms (ADR-0005) is what catches an arm64-only or amd64-only
  breakage before it reaches `main`.
- Colima's VM size (4 CPUs, 8 GiB memory, 60 GiB disk) caps what can run locally;
  the Phase 4 kind cluster has to fit inside it.
