#!/bin/bash
set -e

echo "=========================================="
echo "Order Service - Local Development Setup"
echo "=========================================="

# Check prerequisites
echo "Checking prerequisites..."
command -v java >/dev/null 2>&1 || { echo "Java 21 is required but not installed. Aborting."; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo "Maven 3.9+ is required but not installed. Aborting."; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "Docker is required but not installed. Aborting."; exit 1; }

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d '"' -f 2 | cut -d '.' -f 1)
if [ "$JAVA_VERSION" != "21" ]; then
    echo "Warning: Java 21 is recommended. Current version: $JAVA_VERSION"
fi

echo ""
echo "Step 1: Starting infrastructure services (Jaeger, PostgreSQL)..."
docker-compose up -d

echo ""
echo "Step 2: Waiting for services to be ready..."
sleep 5

echo ""
echo "Step 3: Building the project..."
mvn clean install -DskipTests

echo ""
echo "=========================================="
echo "Setup complete!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "  - Run tests:        make test"
echo "  - Start app:        make run"
echo "  - Run all:          make build-test"
echo "  - View Jaeger UI:   http://localhost:16686"
echo "  - API docs:         http://localhost:8080/swagger-ui.html"
echo "  - Health check:     http://localhost:8080/actuator/health"
echo "  - Prometheus:       http://localhost:8080/actuator/prometheus"
echo ""
echo "To stop infrastructure: make dev-stop"
