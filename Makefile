# Order Service - Makefile

# Variables
SERVICE_NAME := order-service
VERSION := 0.1.0-SNAPSHOT
DOCKER_IMAGE := $(SERVICE_NAME):$(VERSION)
K8S_NAMESPACE := default
HELM_RELEASE := order-service

# Default target
.DEFAULT_GOAL := help

.PHONY: help
help: ## Show this help message
	@echo "Available targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

# Development
.PHONY: dev
dev: ## Start local dependencies (Jaeger, PostgreSQL)
	@echo "Starting local dependencies..."
	docker-compose up -d

.PHONY: dev-stop
dev-stop: ## Stop local dependencies
	@echo "Stopping local dependencies..."
	docker-compose down

# Build
.PHONY: build
build: ## Build order-demo only (Maven)
	@echo "Building project..."
	mvn clean install -DskipTests

.PHONY: build-all
build-all: o11y-build ## Build both o11y-kit and order-demo
	@echo "Building order-demo..."
	mvn clean install -DskipTests

.PHONY: o11y-build
o11y-build: ## Build o11y-kit modules
	@echo "Building o11y-kit modules..."
	cd o11y-kit && mvn clean install -DskipTests && cd ..

.PHONY: build-test
build-test: ## Build order-demo with tests
	@echo "Building with tests..."
	mvn clean verify

.PHONY: build-test-all
build-test-all: o11y-test ## Build and test both projects
	@echo "Building and testing order-demo..."
	mvn clean verify

# Testing
.PHONY: test
test: ## Run order-demo unit tests
	@echo "Running unit tests..."
	mvn test

.PHONY: test-all
test-all: o11y-test test ## Run all tests for both projects

.PHONY: o11y-test
o11y-test: ## Run o11y-kit unit tests
	@echo "Running o11y-kit tests..."
	cd o11y-kit && mvn test && cd ..

.PHONY: test-integration
test-integration: ## Run integration tests
	@echo "Running integration tests..."
	mvn verify

.PHONY: test-bdd
test-bdd: ## Run BDD/Cucumber tests
	@echo "Running BDD tests..."
	mvn -pl bdd-specs test -Dtest=CucumberTestSuite

.PHONY: test-arch
test-arch: ## Run ArchUnit architecture tests
	@echo "Running architecture tests..."
	mvn -pl order-infrastructure test -Dtest=ArchitectureTest

.PHONY: test-coverage
test-coverage: ## Run tests with coverage report
	@echo "Running tests with coverage..."
	mvn clean verify jacoco:report

# Application
.PHONY: run
run: ## Start the Spring Boot application
	@echo "Starting application..."
	mvn -pl order-infrastructure spring-boot:run

.PHONY: run-profile
run-profile: ## Start with a specific profile (usage: make run-profile PROFILE=dev)
	@echo "Starting application with profile $(PROFILE)..."
	mvn -pl order-infrastructure spring-boot:run -Dspring-boot.run.profiles=$(PROFILE)

# Docker
.PHONY: docker-build
docker-build: ## Build Docker image
	@echo "Building Docker image..."
	docker build -t $(DOCKER_IMAGE) .

.PHONY: docker-run
docker-run: ## Run Docker container
	@echo "Running Docker container..."
	docker run -p 8080:8080 $(DOCKER_IMAGE)

.PHONY: docker-push
docker-push: ## Push Docker image to registry (set REGISTRY variable)
	@echo "Pushing Docker image..."
	docker tag $(DOCKER_IMAGE) $(REGISTRY)/$(DOCKER_IMAGE)
	docker push $(REGISTRY)/$(DOCKER_IMAGE)

# Kubernetes
.PHONY: k8s-deploy
k8s-deploy: ## Deploy to Kubernetes using raw manifests
	@echo "Deploying to Kubernetes..."
	kubectl apply -f k8s/

.PHONY: k8s-delete
k8s-delete: ## Delete Kubernetes resources
	@echo "Deleting Kubernetes resources..."
	kubectl delete -f k8s/

.PHONY: k8s-status
k8s-status: ## Check Kubernetes deployment status
	@echo "Checking deployment status..."
	kubectl get pods -l app=order-service
	kubectl get svc order-service

# Helm
.PHONY: helm-install
helm-install: ## Install/upgrade Helm chart
	@echo "Installing Helm chart..."
	helm upgrade --install $(HELM_RELEASE) helm/order-service --namespace $(K8S_NAMESPACE)

.PHONY: helm-uninstall
helm-uninstall: ## Uninstall Helm chart
	@echo "Uninstalling Helm chart..."
	helm uninstall $(HELM_RELEASE) --namespace $(K8S_NAMESPACE)

.PHONY: helm-template
helm-template: ## Render Helm templates without deploying
	@echo "Rendering Helm templates..."
	helm template $(HELM_RELEASE) helm/order-service

# Cleanup
.PHONY: clean
clean: ## Clean build artifacts
	@echo "Cleaning build artifacts..."
	mvn clean
	rm -rf target/

.PHONY: clean-all
clean-all: clean ## Clean everything including Docker images
	@echo "Removing Docker images..."
	docker rmi $(DOCKER_IMAGE) || true

# Utilities
.PHONY: format
format: ## Format code
	@echo "Formatting code..."
	mvn spotless:apply

.PHONY: lint
lint: lint-markdown lint-yaml lint-docker lint-spotbugs ## Run all linters locally

.PHONY: lint-markdown
lint-markdown: ## Lint Markdown files
	@echo "Linting Markdown files..."
	@which markdownlint-cli2 2>/dev/null 1>&2 && \
		markdownlint-cli2 --config .markdownlint-cli2.jsonc \
			'*.md' '!docs/RESUME_GUIDE.md' '!docs/SPEC.md' \
			'docs/**/*.md' 'o11y-kit/**/*.md' || \
		echo "  Install: npm install -g markdownlint-cli2"

.PHONY: lint-yaml
lint-yaml: ## Lint YAML files
	@echo "Linting YAML files (GitHub Actions, K8s, Helm)..."
	@which yamllint 2>/dev/null 1>&2 && \
		yamllint -d '{extends: relaxed, rules: {line-length: disable, document-start: disable, truthy: disable}}' \
			.github/workflows/ k8s/ helm/ || \
		echo "  Install: pip install yamllint"

.PHONY: lint-docker
lint-docker: ## Lint Dockerfile
	@echo "Linting Dockerfile..."
	@which hadolint 2>/dev/null 1>&2 && \
		hadolint Dockerfile || \
		echo "  Install: https://github.com/hadolint/hadolint/releases"

.PHONY: lint-spotbugs
lint-spotbugs: ## Run SpotBugs + Checkstyle (o11y-kit modules)
	@echo "Running SpotBugs + Checkstyle..."
	@cd o11y-kit && mvn verify -P static-analysis -B && cd ..

.PHONY: codeql
codeql: ## Run CodeQL analysis locally (requires CodeQL CLI)
	@echo "Running CodeQL analysis..."
	@which codeql 2>/dev/null 1>&2 && \
		codeql database create target/codeql-db --language=java --source-root=. --command='mvn clean verify -DskipTests -B' && \
		codeql database analyze target/codeql-db --format=sarif-latest --output=target/codeql-results.sarif && \
		echo "  Results: target/codeql-results.sarif" || \
		echo "  Install: https://github.com/github/codeql-cli-binaries/releases"

.PHONY: scan-all
scan-all: lint dependency-check test-coverage ## Run all quality gate checks (lint + OWASP + coverage)
	@echo "All quality checks complete."

.PHONY: dependency-check
dependency-check: ## Run OWASP dependency check
	@echo "Running OWASP dependency check..."
	mvn org.owasp:dependency-check-maven:check
