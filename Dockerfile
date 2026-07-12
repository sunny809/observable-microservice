# Multi-stage build for order-service
# Stage 1: Build o11y-kit SDK (dependency)
FROM maven:3.9-eclipse-temurin-25-alpine AS o11y-builder
WORKDIR /build/o11y-kit
COPY o11y-kit/pom.xml o11y-kit/
COPY o11y-kit/o11y-kit-api/pom.xml o11y-kit/o11y-kit-api/
COPY o11y-kit/o11y-kit-micrometer/pom.xml o11y-kit/o11y-kit-micrometer/
COPY o11y-kit/o11y-kit-spring-webmvc/pom.xml o11y-kit/o11y-kit-spring-webmvc/
COPY o11y-kit/o11y-kit-spring-webflux/pom.xml o11y-kit/o11y-kit-spring-webflux/
COPY o11y-kit/o11y-kit-spring-boot-autoconfigure/pom.xml o11y-kit/o11y-kit-spring-boot-autoconfigure/
COPY o11y-kit/o11y-kit-spring-boot-starter/pom.xml o11y-kit/o11y-kit-spring-boot-starter/
COPY o11y-kit/o11y-kit-test/pom.xml o11y-kit/o11y-kit-test/
RUN mvn dependency:go-offline -B
COPY o11y-kit/ o11y-kit/
RUN mvn clean install -DskipTests -B

# Stage 2: Build order-demo with Maven
FROM maven:3.9-eclipse-temurin-25-alpine AS builder
WORKDIR /build
COPY --from=o11y-builder /root/.m2/repository /root/.m2/repository
COPY pom.xml .
COPY order-application/pom.xml order-application/
COPY order-adapter/pom.xml order-adapter/
COPY order-infrastructure/pom.xml order-infrastructure/
COPY order-o11y/pom.xml order-o11y/
COPY bdd-specs/pom.xml bdd-specs/
RUN mvn dependency:go-offline -B
COPY . .
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime with distroless JRE for security hardening
FROM eclipse-temurin:25-jre-alpine

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy application JAR
COPY --from=builder /build/order-infrastructure/target/*.jar app.jar

# Set ownership to non-root user
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
