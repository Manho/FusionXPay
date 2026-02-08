# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy entire project for proper multi-module build
COPY pom.xml ./pom.xml
COPY common ./common
COPY services ./services

# Build common module first, then the target service
RUN mvn install -pl common -am -DskipTests -q
RUN mvn package -pl services/payment-service -am -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/services/payment-service/target/*.jar app.jar

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
