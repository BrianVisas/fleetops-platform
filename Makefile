# One entry point for the whole repo.
# Targets are filled in phase by phase.

.PHONY: help build test up down

help:
	@echo "make build   - build all services      (Phase 1)"
	@echo "make test    - run all unit tests      (Phase 1)"
	@echo "make up      - start the local stack   (Phase 1)"
	@echo "make down    - stop the local stack    (Phase 1)"

build:
	@echo "TODO Phase 1: build fleet-api, telemetry-ingest, vehicle-sim"

test:
	@echo "TODO Phase 1: run unit tests for every service"

up:
	@echo "TODO Phase 1: docker compose -f deploy/compose/docker-compose.yml up -d"

down:
	@echo "TODO Phase 1: docker compose -f deploy/compose/docker-compose.yml down"
