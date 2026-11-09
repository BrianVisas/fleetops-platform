# Changelog

## [0.2.0](https://github.com/BrianVisas/fleetops-platform/compare/v0.1.0...v0.2.0) (2026-11-09)


### Bug fixes

* **ci:** pin trivy-action to a commit SHA and the local scanners to versions ([d370005](https://github.com/BrianVisas/fleetops-platform/commit/d3700054ccf87e22ad11edbd82a0095fb4afd03a))
* **coverage:** keep integration tests out of the unit coverage gate ([e81350b](https://github.com/BrianVisas/fleetops-platform/commit/e81350bcc947c36a0f74eeb6219742751f95355f))
* **coverage:** set the JaCoCo floor from a real measurement ([7912049](https://github.com/BrianVisas/fleetops-platform/commit/7912049117a9142e87b0086447f458d137849b8b))
* **images:** patch base-image packages and move off end-of-life Alpine ([eb24314](https://github.com/BrianVisas/fleetops-platform/commit/eb243145b1836d29f9caa622cd93e1c63fb58ea7))
* **lint:** run errcheck on its defaults ([4d4924e](https://github.com/BrianVisas/fleetops-platform/commit/4d4924ecdc515a48f15a1985143d5b8ef9796d9b))
* **security:** scan offline, fail on end-of-life images, drop the dead exception ([fff45cc](https://github.com/BrianVisas/fleetops-platform/commit/fff45cc87abd7d277fd2483916ebe5dbafb8d24c))


### CI and delivery

* act on the first security scan ([26b31f7](https://github.com/BrianVisas/fleetops-platform/commit/26b31f7ff1243409084bcd536cdc78c88bf3e5e6))
* build and unit test workflows for java and go services ([186ba42](https://github.com/BrianVisas/fleetops-platform/commit/186ba4274be72164a14cab0f4df693c30ddacbae))
* build and unit test workflows for java and go services ([5267ef0](https://github.com/BrianVisas/fleetops-platform/commit/5267ef0e2af3c8475741e2f555a505d004ecd4b9))
* coverage gates and go linting ([37e6c06](https://github.com/BrianVisas/fleetops-platform/commit/37e6c0611977b0380baef4eac7face133616c189))
* coverage gates and go linting ([b96a690](https://github.com/BrianVisas/fleetops-platform/commit/b96a690bbcefa12abc1f96a4e685bdad94d7f18c))
* publish multi-arch images to GHCR and version releases with release-please ([6ed2463](https://github.com/BrianVisas/fleetops-platform/commit/6ed24638a7663083c07f6265970657a2f12edb29))
* publish multi-arch images to GHCR and version releases with release-please ([7b9455a](https://github.com/BrianVisas/fleetops-platform/commit/7b9455a1b83c31285879d91ccd71da794d0ce96f))
* security scanning, and fixes for what it found ([6cd1af3](https://github.com/BrianVisas/fleetops-platform/commit/6cd1af30195f4768564c8d9f19ea0152c06258e4))


### Build

* **go:** pin the Go toolchain to 1.27.3 ([9c04489](https://github.com/BrianVisas/fleetops-platform/commit/9c04489889d1f503b13b55254d371684e7e3057d))
* **images:** run build stages on the build platform and cross-compile ([325df13](https://github.com/BrianVisas/fleetops-platform/commit/325df13bf3cf62de33c4bf2affd6db58996d2373))


### Tests

* integration tests against a real postgres ([7acfce6](https://github.com/BrianVisas/fleetops-platform/commit/7acfce6f19dd25245c6ba7860dadbadc8ebaaf6a))
* integration tests against a real postgres ([3abff4a](https://github.com/BrianVisas/fleetops-platform/commit/3abff4a855c55772d889c15b518cdc891f1c8e9e))
* make integration tests independent of class order ([b36b9f2](https://github.com/BrianVisas/fleetops-platform/commit/b36b9f24fa059c8159bef05379ad4aced4e4c0ea))


### Documentation

* add an engineering log ([62b257f](https://github.com/BrianVisas/fleetops-platform/commit/62b257fdc80b318c52bf5011849fd26a90f66bc9))
* add an engineering log ([ac2c2b1](https://github.com/BrianVisas/fleetops-platform/commit/ac2c2b1c5da415db2221966bbe12d173ed8a397d))
* **adr:** ADR-0005 image publishing and versioning ([caf83a8](https://github.com/BrianVisas/fleetops-platform/commit/caf83a814c2724025d18a4b566b954215e1525c6))
* **security:** record the second triage ([6ce7ae2](https://github.com/BrianVisas/fleetops-platform/commit/6ce7ae27b3194475e9857421bd22ecea999f85db))
* **security:** record the trivy-action pin and why tags are not enough ([02fd88b](https://github.com/BrianVisas/fleetops-platform/commit/02fd88b241f866240f64771143f7b33f895bc500))
