# Changelog

## [0.3.0](https://github.com/BrianVisas/fleetops-platform/compare/v0.2.0...v0.3.0) (2026-11-18)


### Features

* **fleet-api:** auto-pause a rollout when failures pass a threshold ([#38](https://github.com/BrianVisas/fleetops-platform/issues/38)) ([16bed8f](https://github.com/BrianVisas/fleetops-platform/commit/16bed8fd1f107e5599ddbf9636e00b18911c9a99))
* **web-ui:** dashboard for vehicles, releases and rollouts ([#41](https://github.com/BrianVisas/fleetops-platform/issues/41)) ([87634c8](https://github.com/BrianVisas/fleetops-platform/commit/87634c8ba131c9ef4e1083ef5ba93d6374f39464))


### Bug fixes

* **deps:** Go 1.27.4 for GO-2026-6859 in net/http ([#42](https://github.com/BrianVisas/fleetops-platform/issues/42)) ([5b16327](https://github.com/BrianVisas/fleetops-platform/commit/5b16327a0c4f00feec0d061f3d6b2f7d75cd233e))
* **e2e:** run the suites in Playwright's Docker images ([#49](https://github.com/BrianVisas/fleetops-platform/issues/49)) ([70f1ec2](https://github.com/BrianVisas/fleetops-platform/commit/70f1ec2ffd10fb1a1dfa026506bbb4a19d41c66b))


### CI and delivery

* control how dependencies and runners change ([#29](https://github.com/BrianVisas/fleetops-platform/issues/29)) ([2e7f73e](https://github.com/BrianVisas/fleetops-platform/commit/2e7f73eac5038ca902bd0d40657fcc2256e8be82))


### Build

* **deps-dev:** bump the acceptance-java group across 1 directory with 4 updates ([#35](https://github.com/BrianVisas/fleetops-platform/issues/35)) ([ddc0b11](https://github.com/BrianVisas/fleetops-platform/commit/ddc0b11d84583091cadb664b32e156397e593ea4))
* **deps:** bump actions/setup-python from 6 to 7 in the actions group across 1 directory ([#36](https://github.com/BrianVisas/fleetops-platform/issues/36)) ([79bf35f](https://github.com/BrianVisas/fleetops-platform/commit/79bf35f0556e32cc92431eff3f51bd3b2a29655f))
* **deps:** bump the acceptance-python group across 1 directory with 2 updates ([#39](https://github.com/BrianVisas/fleetops-platform/issues/39)) ([2196985](https://github.com/BrianVisas/fleetops-platform/commit/21969850fc5c4ae8bfd6f4b57cf3a2f9d981fb78))
* **deps:** bump the actions group with 4 updates ([#31](https://github.com/BrianVisas/fleetops-platform/issues/31)) ([e8f5735](https://github.com/BrianVisas/fleetops-platform/commit/e8f5735831fae4aa4526cface4400973cafc6da7))
* **deps:** bump the maven-build group in /services/fleet-api with 2 updates ([db70d6e](https://github.com/BrianVisas/fleetops-platform/commit/db70d6e34e59a43730e6ecc43cba406229756571))
* **fleet-api:** build the image with the Maven wrapper ([#32](https://github.com/BrianVisas/fleetops-platform/issues/32)) ([1e91c58](https://github.com/BrianVisas/fleetops-platform/commit/1e91c583acdf5f96ad2cb19a7b7eca0387db3eaa))


### Tests

* acceptance scenarios for releases, rollouts and update polling ([#37](https://github.com/BrianVisas/fleetops-platform/issues/37)) ([1c1e67d](https://github.com/BrianVisas/fleetops-platform/commit/1c1e67de1f5fc161463fadd364c7309d9febc55e))
* acceptance test bake-off for vehicle management ([#33](https://github.com/BrianVisas/fleetops-platform/issues/33)) ([173eabf](https://github.com/BrianVisas/fleetops-platform/commit/173eabff08157bf7da7338e9b75d4e5990d2b2e5))
* **e2e:** Playwright dashboard tests in TypeScript, Java and Python ([#43](https://github.com/BrianVisas/fleetops-platform/issues/43)) ([3283c8a](https://github.com/BrianVisas/fleetops-platform/commit/3283c8ac08fff1a5b311b1b6ee8a8e6efdd0d28a))
* keep three acceptance suites, add telemetry scenarios and a traceability gate ([#34](https://github.com/BrianVisas/fleetops-platform/issues/34)) ([818531d](https://github.com/BrianVisas/fleetops-platform/commit/818531d124b3e529edc2bfbba3725c6e68189da8))


### Documentation

* **adr:** keep the TypeScript and Java Playwright suites (ADR-0007) ([#48](https://github.com/BrianVisas/fleetops-platform/issues/48)) ([413b521](https://github.com/BrianVisas/fleetops-platform/commit/413b521916a0c47517d59e28325d00357a525bf0))
* restore ADR-0001 and ADR-0002 and remove duplicate changelog entries ([#27](https://github.com/BrianVisas/fleetops-platform/issues/27)) ([52055fa](https://github.com/BrianVisas/fleetops-platform/commit/52055fab4b08ad37d6f4ac718040d56dad0247b8))
* test strategy for Phase 3 ([#50](https://github.com/BrianVisas/fleetops-platform/issues/50)) ([ec8584e](https://github.com/BrianVisas/fleetops-platform/commit/ec8584ea5e3077db0a0eabb4afe6a1a942260d78))

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
* build and unit test workflows for java and go services ([5267ef0](https://github.com/BrianVisas/fleetops-platform/commit/5267ef0e2af3c8475741e2f555a505d004ecd4b9))
* coverage gates and go linting ([b96a690](https://github.com/BrianVisas/fleetops-platform/commit/b96a690bbcefa12abc1f96a4e685bdad94d7f18c))
* publish multi-arch images to GHCR and version releases with release-please ([7b9455a](https://github.com/BrianVisas/fleetops-platform/commit/7b9455a1b83c31285879d91ccd71da794d0ce96f))
* security scanning, and fixes for what it found ([6cd1af3](https://github.com/BrianVisas/fleetops-platform/commit/6cd1af30195f4768564c8d9f19ea0152c06258e4))


### Build

* **go:** pin the Go toolchain to 1.27.3 ([9c04489](https://github.com/BrianVisas/fleetops-platform/commit/9c04489889d1f503b13b55254d371684e7e3057d))
* **images:** run build stages on the build platform and cross-compile ([325df13](https://github.com/BrianVisas/fleetops-platform/commit/325df13bf3cf62de33c4bf2affd6db58996d2373))


### Tests

* integration tests against a real postgres ([3abff4a](https://github.com/BrianVisas/fleetops-platform/commit/3abff4a855c55772d889c15b518cdc891f1c8e9e))
* make integration tests independent of class order ([b36b9f2](https://github.com/BrianVisas/fleetops-platform/commit/b36b9f24fa059c8159bef05379ad4aced4e4c0ea))


### Documentation

* add an engineering log ([ac2c2b1](https://github.com/BrianVisas/fleetops-platform/commit/ac2c2b1c5da415db2221966bbe12d173ed8a397d))
* **adr:** ADR-0005 image publishing and versioning ([caf83a8](https://github.com/BrianVisas/fleetops-platform/commit/caf83a814c2724025d18a4b566b954215e1525c6))
* **security:** record the second triage ([6ce7ae2](https://github.com/BrianVisas/fleetops-platform/commit/6ce7ae27b3194475e9857421bd22ecea999f85db))
* **security:** record the trivy-action pin and why tags are not enough ([02fd88b](https://github.com/BrianVisas/fleetops-platform/commit/02fd88b241f866240f64771143f7b33f895bc500))
