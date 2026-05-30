# Multi-stage build for order-service
# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build
COPY pom.xml .
COPY order-application/pom.xml order-application/
COPY order-adapter/pom.xml order-adapter/
COPY order-infrastructure/pom.xml order-infrastructure/
COPY order-o11y/pom.xml order-o11y/
COPY bdd-specs/pom.xml bdd-specs/

RUN mvn dependency:go-offline -B

COPY . .
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime with JRE
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /build/order-infrastructure/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
