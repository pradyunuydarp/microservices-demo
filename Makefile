# Shortcuts. Each target is one plain command; the README lists them for Windows users.
.PHONY: build test up down smoke logs

build: ## Build the five jars (skip tests)
	./mvnw -q package -DskipTests

test: ## Run all tests (verify, not test: the contract stubs are built in package)
	./mvnw verify

up: build ## Build images and start everything, wait until healthy
	docker compose up -d --build --wait

down: ## Stop everything and delete the databases
	docker compose down -v

smoke: ## End-to-end check against the running system
	scripts/smoke.sh

logs: ## Follow the logs of all services
	docker compose logs -f
