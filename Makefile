# One entry point for the whole repo.
COMPOSE := docker compose -f deploy/compose/docker-compose.yml

.PHONY: help build test up down logs sim ps clean fmt

help:
	@echo "make build   - build every service image"
	@echo "make test    - run all unit tests (no database needed)"
	@echo "make up      - start db, fleet-api and telemetry-ingest"
	@echo "make sim     - add the vehicle simulator to a running stack"
	@echo "make logs    - follow logs of the running stack"
	@echo "make ps      - show container status"
	@echo "make down    - stop the stack (keeps the database volume)"
	@echo "make clean   - stop the stack and delete the database volume"
	@echo "make fmt     - format Go code"

build:
	$(COMPOSE) build

test:
	cd services/fleet-api && ./mvnw -q test
	cd services/telemetry-ingest && go test ./...
	cd services/vehicle-sim && go test ./...

up:
	$(COMPOSE) up -d --build
	@echo
	@echo "fleet-api        http://localhost:8080/actuator/health"
	@echo "telemetry-ingest http://localhost:8081/healthz"

sim:
	$(COMPOSE) --profile sim up -d --build vehicle-sim
	$(COMPOSE) logs -f vehicle-sim

logs:
	$(COMPOSE) logs -f

ps:
	$(COMPOSE) ps

down:
	$(COMPOSE) --profile sim down

clean:
	$(COMPOSE) --profile sim down -v

fmt:
	cd services/telemetry-ingest && gofmt -w *.go
	cd services/vehicle-sim && gofmt -w *.go
