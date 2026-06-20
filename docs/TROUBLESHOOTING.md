# Troubleshooting Guide

Common issues encountered during development and their solutions.

## Build Issues

### Tests fail with "Port already in use"

**Cause:** Testcontainers binds to random ports. If tests are interrupted, containers may not be cleaned up.

**Fix:**
```bash
# Clean up orphaned containers
docker system prune -f

# Or stop all running containers
docker stop $(docker ps -q)
```

### Maven build fails with "Could not transfer artifact"

**Cause:** Network issues or Maven Central connectivity problems.

**Fix:**
```bash
# Clear Maven cache and retry
rm -rf ~/.m2/repository/com/example
mvn clean install -U
```

### JaCoCo coverage threshold fails

**Cause:** New code paths not covered by tests.

**Fix:**
```bash
# Generate coverage report to see uncovered lines
mvn clean verify jacoco:report

# Open report in browser
open order-application/target/site/jacoco/index.html
```

## Runtime Issues

### Application fails to start with "Table not found"

**Cause:** Flyway migrations not applied or H2 database not initialized.

**Fix:**
```bash
# Ensure Flyway is enabled
SPRING_FLYWAY_ENABLED=true mvn -pl order-infrastructure spring-boot:run

# Or disable Flyway for local dev
SPRING_FLYWAY_ENABLED=false mvn -pl order-infrastructure spring-boot:run
```

### Jaeger traces not showing

**Cause:** OTLP endpoint misconfigured or Jaeger not running.

**Fix:**
```bash
# Start Jaeger
docker-compose up -d jaeger

# Verify endpoint
curl http://localhost:4317

# Check application.yml
# otel.exporter.endpoint: http://localhost:4317
```

### Inventory service returns 500

**Cause:** Circuit breaker is open due to previous failures.

**Fix:**
```bash
# Check circuit breaker state
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state

# Wait 30 seconds for circuit to close
# Or restart the application
```

### Kafka producer fails with "Connection refused"

**Cause:** Kafka not running or bootstrap servers misconfigured.

**Fix:**
```bash
# Start Kafka and Zookeeper
docker-compose up -d kafka zookeeper

# Verify Kafka is running
docker logs order-service-kafka

# Check application.yml
# spring.kafka.bootstrap-servers: localhost:9092
```

## Test Issues

### BDD tests hang

**Cause:** WireMock ports not released between test runs.

**Fix:**
```bash
# Kill Java processes holding ports
kill $(lsof -t -i:8081,8082)

# Or run with fresh JVM
mvn -pl bdd-specs test -Dtest=CucumberTestSuite
```

### ArchUnit tests fail

**Cause:** New code violates architecture rules (e.g., domain layer depends on adapter).

**Fix:**
```bash
# Check which rule failed
cat order-infrastructure/target/surefire-reports/*.txt

# Common fixes:
# - Move classes to correct packages
# - Use dependency injection instead of direct instantiation
# - Extract interfaces for cross-module dependencies
```

## Docker Issues

### Docker build fails with "no such file or directory"

**Cause:** Multi-stage build references incorrect artifact path.

**Fix:**
```bash
# Ensure Maven build completes first
mvn clean package -DskipTests

# Check artifact exists
ls order-infrastructure/target/*.jar
```

### Container exits immediately

**Cause:** Health check fails or application crashes on startup.

**Fix:**
```bash
# Check logs
docker logs order-service

# Common causes:
# - Database not reachable
# - Kafka not running
# - Port conflict
```

## Kubernetes Issues

### Pod stuck in CrashLoopBackOff

**Cause:** Application fails to start (database unreachable, missing secrets).

**Fix:**
```bash
# Check logs
kubectl logs deployment/order-service

# Check events
kubectl describe pod -l app=order-service

# Common causes:
# - PostgreSQL not running
# - Secrets not applied
# - ConfigMap missing
```

### Readiness probe fails

**Cause:** Application not fully started or downstream services unreachable.

**Fix:**
```bash
# Check readiness endpoint
kubectl exec -it deployment/order-service -- curl localhost:8080/actuator/health/readiness

# Common causes:
# - Inventory service not running
# - WMS service not running
# - Database connection pool exhausted
```

## Performance Issues

### High memory usage

**Cause:** HikariCP connection pool too large or Caffeine cache too big.

**Fix:**
```yaml
# Reduce connection pool size
spring.datasource.hikari.maximum-pool-size: 10

# Reduce cache size
caffeine.cache.idempotency.maximum-size: 1000
```

### Slow response times

**Cause:** Circuit breaker open or database queries not optimized.

**Fix:**
```bash
# Check metrics
curl http://localhost:8080/actuator/metrics/http.server.requests

# Enable SQL logging temporarily
spring.jpa.show-sql: true
```

## Security Issues

### JWT validation fails

**Cause:** JWT issuer URI misconfigured or token expired.

**Fix:**
```bash
# Use local profile to disable JWT
SPRING_PROFILES_ACTIVE=local make run

# Or configure correct issuer
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://auth.example.com
```

### OWASP dependency check fails

**Cause:** Vulnerable dependencies detected.

**Fix:**
```bash
# Check report
cat target/dependency-check-report.html

# Update dependencies
mvn versions:use-latest-versions
```
