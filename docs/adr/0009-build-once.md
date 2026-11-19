# ADR-0009: Build each image once per commit, and test that build

- Status: accepted
- Date: 2026-11-19

## Context

Until this decision, every workflow that needed a running service built its
own image from source. On one pull request, fleet-api was built from scratch
in up to eight jobs: three acceptance suites and two browser suites through
`docker compose up --build`, four image scans through `docker build`, the
cluster install, and the multi-arch release build. Each build downloaded Maven
and all of fleet-api's dependencies from Maven Central.

Maven Central limits "unusually high-volume traffic" from a build environment
or infrastructure provider, and GitHub's runners share their addresses. It
refused downloads in under a second, and kept refusing for minutes: on #57,
two of eight fleet-api builds in one run failed that way, and four runs in two
days lost jobs to it (EL-026, EL-027). Sonatype's advice is explicit: "the
right answer is not to retry harder", but to stop "repeated downloads from
clean or ephemeral environments" and fix "jobs that bypass caches"
([429 FAQ](https://central.sonatype.org/faq/429-error/)).

Two more problems came from the same design:

- **What was tested was not what shipped.** The acceptance suites tested one
  build, the scan another, and `main` published a third. All three came from the
  same commit, but nothing showed they were the same image. ADR-0005 already
  listed the scan's duplicate build as a consequence to remove later.
- **The Actions cache filled up.** Every pull request wrote its own layer
  caches and nothing deleted them; at 10 GB, GitHub evicted `main`'s cache, so
  more builds missed it and downloaded again.

## Options considered

1. **Retry, or retry longer.** EL-026 tried four attempts over 90 seconds. It
   did not save a single build, and Sonatype says retries make the limit worse.
2. **A Maven mirror.** Google runs a public mirror of Maven Central. It is "not
   an officially supported Google product", it lags Central by about a day, and
   Sonatype treats getting around its limits as grounds for harder blocks. A
   repository manager (Nexus, Artifactory) is the standard answer in a company,
   but this project has nowhere free to run one.
3. **Build once per commit, test that build, publish only after it passed.**
   One job builds each image; every job that needs an image loads that build;
   the release build reuses its layers and waits for all the tests.

## Decision

Option 3, in one workflow, `ci.yml`:

- An `images` job builds the four images for amd64 and passes them to the
  other jobs as artifacts (`fleetops/<service>:ci`). Its layer cache has one
  scope per service; GitHub keeps caches per branch, so a pull request reads
  `main`'s and its own, and writes its own.
- The image scan, the acceptance suites, the browser suites and the cluster
  install load those images. Compose starts them with `--no-build`, and the
  cluster install with `IMAGE_TAG=ci`. A shared step,
  `.github/actions/load-images`, fails a job that got no images.
- The multi-arch release build runs after all of them, reads the same cache,
  and adds only the arm64 runtime stage. On `main` it publishes.
- `cache-cleanup.yml` deletes a pull request's caches when it closes.
- The retry loop in fleet-api's Dockerfile goes.

`acceptance.yml`, `e2e.yml`, `cluster.yml` and `release.yml` stay as separate
files, called by `ci.yml` as reusable workflows, so each stays readable on its
own. `security.yml` keeps the checks that read the source (gitleaks, the config
scan, govulncheck); the image scan moved to `ci.yml`, next to the images.

## Consequences

- Maven Central is contacted once per commit for fleet-api, by the `images`
  job, and only when the cache does not have the layers; before, up to eight
  times. The release build downloads nothing.
- The scanned, tested and installed image is one build. On `main`, the
  published amd64 image is rebuilt from that build's cached layers: the same
  layers, but not yet the same digest. Promoting by digest is part of how a new
  image reaches `dev` (ADR-0010, Phase 4 package 5).
- Nothing is published unless the scan, all five suites and the cluster
  install passed. A red test on `main` now holds back `:main` and
  `:sha-<commit>`.
- Pull requests wait longer for the multi-arch build: it starts after the
  tests, not beside them.
- The check names change, for example `ci / acceptance / acceptance (cucumber)`.
  No branch ruleset refers to them yet.
- The cluster install now runs on every pull request, not only when
  `infra/local` or `deploy/charts` change: the services run there too. That
  costs about four runner minutes per pull request.
- The nightly run builds without the cache, so the scan sees that day's base
  images and packages, and it refreshes `main`'s cache. It downloads Maven once
  a night.
- Not covered: `fleet-api.yml` and the Cucumber and Playwright-Java suites
  still run their own Maven for their tests. They now cache the Maven the
  wrapper downloads as well as the dependencies, so they reach Maven Central
  only when a version changes.
