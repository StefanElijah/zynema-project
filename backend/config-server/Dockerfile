# Multi-stage build for zynema-config-server
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/common/pom.xml backend/common/pom.xml
COPY backend/event-contracts/pom.xml backend/event-contracts/pom.xml
COPY backend/config-server/pom.xml backend/config-server/pom.xml
RUN cd backend && mvn -B -pl config-server -am -DskipTests dependency:go-offline
COPY backend/ backend/
RUN cd backend && mvn -B -pl config-server -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY --from=build /workspace/backend/config-server/target/zynema-config-server.jar app.jar
EXPOSE 8888
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"
HEALTHCHECK --interval=15s --timeout=3s --retries=5 --start-period=30s \
  CMD wget -qO- http://localhost:8888/actuator/health/liveness || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
