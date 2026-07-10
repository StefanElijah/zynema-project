FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/common/pom.xml backend/common/pom.xml
COPY backend/event-contracts/pom.xml backend/event-contracts/pom.xml
COPY backend/auth-service/pom.xml backend/auth-service/pom.xml
RUN cd backend && mvn -B -pl auth-service -am -DskipTests dependency:go-offline
COPY backend/ backend/
RUN cd backend && mvn -B -pl auth-service -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY --from=build /workspace/backend/auth-service/target/zynema-auth-service.jar app.jar
EXPOSE 8082
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"
HEALTHCHECK --interval=15s --timeout=3s --retries=5 --start-period=30s 
  CMD wget -qO- http://localhost:8082/actuator/health/liveness || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
