// Dockerfile.shared - Multi-stage build template for Java services
// Included via COPY in each service's Dockerfile
// Place at: infra/docker/Dockerfile.shared
//
// This file documents the build pattern used by all zynema-* services.
// Each service's Dockerfile copies the relevant pieces and runs mvn.
