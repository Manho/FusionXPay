# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy parent pom and common module first (for dependency caching)
COPY pom.xml /app/parent-pom.xml
COPY common/pom.xml /app/common/pom.xml
COPY common/src /app/common/src

# Copy this service
COPY services/api-gateway/pom.xml /app/services/api-gateway/pom.xml
COPY services/api-gateway/src /app/services/api-gateway/src

# Build
WORKDIR /app
RUN mvn -f parent-pom.xml install -pl common -am -DskipTests
WORKDIR /app/services/api-gateway
RUN mvn package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/services/api-gateway/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
