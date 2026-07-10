# ADR-0001: Monorepo multi-stack con Nx

- **Status:** Accepted
- **Date:** 2026-07-09
- **Deciders:** Project owner, AI co-pilot

## Context

Zynema has a Java/Spring Boot backend and a TypeScript/React frontend. We
also have infrastructure-as-code (Docker Compose, K8s manifests) and shared
docs. We need to choose a layout.

## Options considered

1. **Polyrepo (one repo per major piece)**: `zynema-backend`,
   `zynema-frontend`, `zynema-infra`. Each independent.
2. **pnpm workspaces + Maven multi-module + Makefile** (no Nx).
3. **Nx monorepo multi-stack.**

## Decision

We adopt **option 3: Nx monorepo multi-stack**.

## Rationale

- pnpm workspaces don't support Maven natively.
- Turborepo is Node-only.
- Nx has first-class plugins for both Maven and Vite, can run Java tests,
  parallelize builds, and cache results across machines.
- The operational cost of Nx (an `nx.json` + dependency graph) is small
  compared to the productivity win.

## Consequences

- All engineers learn one tool (`nx`) instead of three.
- `nx run-many -t test` runs all unit tests in the right order.
- `nx affected` lets us test only the parts that changed in a PR.
- We must keep `nx.json` honest: if a project is added, it must be
  registered.

## Alternatives revisited

If Nx turns out to be a hindrance, we can fall back to pnpm workspaces +
Makefile. The `Makefile` already has the relevant targets.
