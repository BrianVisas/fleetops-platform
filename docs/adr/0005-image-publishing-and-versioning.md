# ADR-0005: Publish multi-arch images to GHCR, versioned by release-please

- Status: accepted
- Date: 2026-11-09

## Context

Phase 2 ends with images that can be deployed, not just built. Three things have
to be decided: where the images live, which architectures they are built for, and
how versions are assigned.

Constraints. Development happens on an arm64 MacBook; CI runners and the cloud
clusters planned for Phase 6 (AKS, SKE) are amd64. The repository is private, on a
free GitHub plan. Commits already follow Conventional Commits. The services are
released together, as one platform, at the end of each phase (v0.1.0, v0.2.0, ...).

## Options considered

**Registry**

1. **GHCR (ghcr.io).** Authenticates with the workflow's own `GITHUB_TOKEN`, so
   there is no secret to create or rotate. Packages inherit the repository's
   visibility. Ties the images to GitHub.
2. **Docker Hub.** The default everyone knows, but it needs a stored access token,
   and anonymous pulls are rate-limited.
3. **A cloud registry (ACR, STACKIT registry).** Closest to where the images will
   run, but it requires the cloud accounts that only exist from Phase 6.

**Architectures**

1. **amd64 only.** Fastest CI, but the images would not run natively on the
   development machine.
2. **amd64 and arm64, emulating the whole build under QEMU.** Simple, but a Maven
   or Go build under emulation is several times slower.
3. **amd64 and arm64, cross-building.** Build stages run on the CI machine's own
   architecture (`FROM --platform=$BUILDPLATFORM`): Go cross-compiles natively,
   and a jar is architecture-independent anyway. Only the short runtime stages
   (package upgrade, user creation) run under QEMU.

**Versioning**

1. **Manual tags.** Nothing to set up, and nothing stops a forgotten changelog or
   a skipped version.
2. **Per-service versions (release-please manifest with one component per
   service).** Accurate when services ship independently, but they do not: a
   phase changes all three, and per-service tags would multiply for no benefit.
3. **One platform version (release-please, single package at the root).** Reads
   the Conventional Commits since the last release, keeps a release pull request
   with the changelog up to date, and creates the tag and GitHub release when that
   pull request is merged.

## Decision

GHCR, both architectures by cross-building, and one platform version managed by
release-please.

Images are named `ghcr.io/brianvisas/fleetops-<service>`. Every push to `main`
publishes `:main` and `:sha-<commit>`; a release additionally publishes the version
(`:0.2.0`) and the minor line (`:0.2`). Each image carries BuildKit provenance and
an SBOM as attestations. Pull requests run the same multi-arch build without
pushing.

Images are published from a job that depends on release-please in the same
workflow, not from a workflow triggered by the release tag: a tag pushed with
`GITHUB_TOKEN` does not trigger other workflows, and working round that would need
a personal access token stored as a secret.

Which version a phase ends on is a decision, not a side effect of commit types. A
`Release-As:` footer in a commit sets it when the commits alone would suggest
something else, as for v0.2.0, whose commits are mostly `ci:`, `test:` and `fix:`.

## Consequences

- Deployments from Phase 4 on reference images by version or by commit, and the
  commit tag ties every running container back to the exact source.
- The same image runs on the MacBook and in the cloud; "works on my machine" no
  longer depends on architecture.
- Pull requests take longer: each one now builds three images for two platforms.
  The GitHub Actions layer cache keeps unchanged dependency layers from being
  rebuilt.
- Pulling a private image locally needs `docker login ghcr.io` with a token that
  has the `read:packages` scope.
- The release pull request is opened with `GITHUB_TOKEN`, so CI does not run on it.
  That is acceptable while it only changes the changelog and version files, and it
  has to be accounted for when required status checks are introduced.
- The provenance and SBOM attestations are not signed. Signing with cosign, and
  verifying signatures at deploy time, belong to the platform-engineering phase.
- The security workflow still builds its own single-arch copy of each image to
  scan. Scanning the published image instead would remove the duplicate build; it
  is left for later, when deployments start depending on the published images.
