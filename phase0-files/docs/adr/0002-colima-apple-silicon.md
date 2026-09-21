# ADR-0002: Colima as the local container runtime, multi-arch images built in CI

- Status: accepted
- Date: 2026-09-21

## Context

Development happens on a MacBook Pro with an M1 chip, so locally built images are
`arm64`. The target clusters (AKS, STACKIT SKE) run `amd64` nodes. I need a container
runtime on macOS and a rule for which images are allowed to reach a registry.

## Options considered

1. **Docker Desktop** - easiest setup, works with Testcontainers out of the box.
   Free for personal use, but a heavier VM and a licence that depends on company size.
2. **Colima** - free and open source, thin CLI over a Lima VM, resources set
   explicitly, supports Rosetta for running `amd64` images. Needs two environment
   variables for Testcontainers and has no GUI.
3. **Rancher Desktop / OrbStack** - both work; OrbStack is the fastest on Apple
   Silicon but its licence is only free for personal use.

## Decision

Use Colima with the `vz` VM type and Rosetta enabled:

```
colima start --cpu 4 --memory 8 --disk 60 --vm-type vz --vz-rosetta
```

Images that go to a registry are built only in CI, for `linux/amd64` and
`linux/arm64` together, using `docker buildx`. No image is pushed from the laptop.

## Consequences

- Local runs on the Mac and cloud runs on `amd64` use the same image digest set,
  so "works on my machine" differences are caught in CI.
- CI builds take longer because of the second architecture; caching matters.
- Testcontainers needs `DOCKER_HOST` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`
  exported (documented in the README in Phase 2).
- Any base image without an `arm64` variant must be replaced or run through Rosetta
  emulation locally.
