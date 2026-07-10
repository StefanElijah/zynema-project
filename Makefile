# ──────────────────────────────────────────────────────────────
# Makefile — zynema-project
# Quick commands to manage the local development environment.
# ──────────────────────────────────────────────────────────────
SHELL := /bin/bash
.DEFAULT_GOAL := help

COMPOSE := docker compose

.PHONY: help
help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

# ════════════════════ ENVIRONMENT ════════════════════

.PHONY: env
env: ## Create .env file from template if not exists
	@test -f .env || cp .env.example .env
	@echo ".env ready (edit it with your secrets)"

.PHONY: check
check: ## Verify required tools are installed
	@echo "Checking required tooling..."
	@command -v docker >/dev/null 2>&1 || { echo "❌ docker not found"; exit 1; }
	@command -v pnpm >/dev/null 2>&1 || { echo "❌ pnpm not found"; exit 1; }
	@command -v java >/dev/null 2>&1 || { echo "❌ java not found"; exit 1; }
	@command -v mvn >/dev/null 2>&1 || { echo "❌ maven not found"; exit 1; }
	@echo "✅ All required tools present"

# ════════════════════ DOCKER STACK ════════════════════

.PHONY: up-core
up-core: env ## Start core (Eureka, Config, Gateway, DBs, Kafka, base services)
	$(COMPOSE) --profile core up -d
	@echo "✅ Core stack up. Eureka: http://localhost:8761"

.PHONY: up
up: env ## Start core + auth
	$(COMPOSE) --profile core --profile auth up -d
	@echo "✅ Stack up. Eureka: http://localhost:8761 | Keycloak: http://localhost:8081"

.PHONY: up-full
up-full: env ## Start everything (requires 16GB+ RAM)
	$(COMPOSE) --profile core --profile auth --profile storage --profile observability up -d
	@echo "✅ Full stack up. Grafana: http://localhost:3000"

.PHONY: down
down: ## Stop all services (keeps volumes)
	$(COMPOSE) down

.PHONY: clean
clean: ## Stop all services and delete volumes
	$(COMPOSE) down -v

.PHONY: logs
logs: ## Follow logs from all services
	$(COMPOSE) logs -f

.PHONY: logs-%
logs-%: ## Follow logs from a specific service (e.g. make logs-gateway)
	$(COMPOSE) logs -f $*

.PHONY: ps
ps: ## List running services
	$(COMPOSE) ps

.PHONY: restart-%
restart-%: ## Restart a specific service
	$(COMPOSE) restart $*

# ════════════════════ FRONTEND ════════════════════

.PHONY: install
install: ## Install all dependencies (pnpm workspace)
	pnpm install

.PHONY: dev-fe
dev-fe: ## Run frontend dev server (Vite)
	pnpm dev:frontend

.PHONY: build-fe
build-fe: ## Build frontend
	pnpm build:frontend

.PHONY: test-fe
test-fe: ## Run frontend tests
	pnpm test:frontend

.PHONY: lint
lint: ## Lint all
	pnpm lint

.PHONY: format
format: ## Format all code
	pnpm format

# ════════════════════ BACKEND ════════════════════

.PHONY: build-be
build-be: ## Build all backend modules
	cd backend && mvn -B -DskipTests clean install

.PHONY: test-be
test-be: ## Run all backend tests
	cd backend && mvn -B test

.PHONY: install-be
install-be: ## Install backend deps (compile)
	cd backend && mvn -B -DskipTests clean install

.PHONY: run-eureka
run-eureka: ## Run Eureka server locally (requires Java)
	cd backend/eureka-server && mvn spring-boot:run

.PHONY: run-gateway
run-gateway: ## Run API gateway locally
	cd backend/api-gateway && mvn spring-boot:run

.PHONY: run-catalog
run-catalog: ## Run catalog service locally
	cd backend/catalog-service && mvn spring-boot:run

# ════════════════════ UTILITIES ════════════════════

.PHONY: nx-graph
nx-graph: ## Show Nx task graph
	pnpm graph

.PHONY: nx-reset
nx-reset: ## Clear Nx cache
	pnpm reset

.PHONY: postgres-shell
postgres-shell: ## Open psql against the dev database
	$(COMPOSE) exec postgres psql -U zynema -d zynema

.PHONY: redis-cli
redis-cli: ## Open redis-cli
	$(COMPOSE) exec redis redis-cli

.PHONY: kafka-topics
kafka-topics: ## List Kafka topics
	$(COMPOSE) exec kafka kafka-topics --bootstrap-server localhost:9092 --list

.PHONY: keycloak-shell
keycloak-shell: ## Open kc.sh inside Keycloak container
	$(COMPOSE) exec keycloak /bin/bash

.PHONY: seed
seed: ## Load demo seed data
	./infra/scripts/seed.sh

.PHONY: transcode
transcode: ## Transcode demo videos with FFmpeg
	./infra/scripts/transcode.sh
