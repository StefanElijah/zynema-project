# syntax=docker/dockerfile:1.7

# Multi-stage build for zynema-eureka-server
# Build context is the repository root (see docker-compose.yml).
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY backend/ backend/
RUN --mount=type=cache,target=/root/.m2 \
    cd backend && mvn -B -pl eureka-server -am -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY --from=build /workspace/backend/eureka-server/target/zynema-eureka-server.jar app.jar
EXPOSE 8761
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"
HEALTHCHECK --interval=15s --timeout=3s --retries=5 --start-period=30s \
  CMD wget -qO- http://localhost:8761/actuator/health/liveness || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
