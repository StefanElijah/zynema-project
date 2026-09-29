# syntax=docker/dockerfile:1.7

# Multi-stage build for zynema-video-worker.
# Build context is the repository root (see docker-compose.yml).
#
# The runtime image is the only one in the platform that ships FFmpeg: the
# worker shells out to it, so this is the piece that must not share a base
# image with the services.
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY backend/ backend/
RUN --mount=type=cache,target=/root/.m2 \
    cd backend && mvn -B -pl video-worker -am -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
# ffmpeg (with ffprobe) is the pipeline's engine.
RUN apk add --no-cache ffmpeg
RUN addgroup -S worker && adduser -S worker -G worker
USER worker:worker
WORKDIR /app
COPY --from=build /workspace/backend/video-worker/target/zynema-video-worker.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"
# No healthcheck and no exposed port: the container runs one job and exits.
# $WORKER_ARGS allows baked-in defaults; "$@" is how `docker compose run`
# passes the job (--init-storage, --import-source=..., --content=...).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar $WORKER_ARGS \"$@\"", "--"]
