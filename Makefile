# One entry point for the whole repo.
COMPOSE := docker compose -f deploy/compose/docker-compose.yml

.PHONY: help build test up down logs sim ps clean fmt cluster-up cluster-deploy cluster-down cluster-status

help:
	@echo "make build   - build every service image"
	@echo "make test    - run all unit tests (no database needed)"
	@echo "make up      - start db, fleet-api, telemetry-ingest and web-ui"
	@echo "make sim     - add the vehicle simulator to a running stack"
	@echo "make logs    - follow logs of the running stack"
	@echo "make ps      - show container status"
	@echo "make down    - stop the stack (keeps the database volume)"
	@echo "make clean   - stop the stack and delete the database volume"
	@echo "make fmt     - format Go code"
	@echo
	@echo "make cluster-up     - create the local kind cluster with Envoy Gateway and CloudNativePG (infra/local)"
	@echo "make cluster-deploy - install FleetOps into the cluster's dev environment"
	@echo "make cluster-status - show nodes, pods and the gateway"
	@echo "make cluster-down   - delete the local kind cluster"

build:
	$(COMPOSE) build

test:
	cd services/fleet-api && ./mvnw -q test
	cd services/telemetry-ingest && go test ./...
	cd services/vehicle-sim && go test ./...

up:
	$(COMPOSE) up -d --build --wait
	@echo
	@echo "fleet-api        http://localhost:8080/actuator/health"
	@echo "telemetry-ingest http://localhost:8081/healthz"
	@echo "web-ui           http://localhost:3000"

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

# Local Kubernetes (Phase 4). The Compose stack and the cluster each need a few
# GiB; stop one (make down) before starting the other on an 8 GiB Mac.
cluster-up:
	infra/local/cluster-up.sh

cluster-deploy:
	infra/local/install-fleetops.sh

cluster-status:
	kubectl get nodes
	kubectl get pods -A
	kubectl get gateway -A
	kubectl get cluster.postgresql.cnpg.io -A

cluster-down:
	infra/local/cluster-down.sh
