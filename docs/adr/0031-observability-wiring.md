# ADR-0031: Observability wiring — file_sd bridge, alert fan-out, span metrics

- **Status:** Accepted
- **Date:** 2026-09-29
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 9 (observability end-to-end)

## Context

The observability stack (Prometheus, Grafana, Loki, Tempo) existed since Fase 0
but was only half-wired: Grafana had no provisioned datasources or dashboards,
Prometheus had no alert rules and no Alertmanager, Promtail scraped host paths
that do not exist on Docker Desktop and produced no service labels, and logs
carried no trace ids, so the three signals could not reference each other.

Two questions needed a decision rather than a config edit:

1. **How does Prometheus find the 9 services?** The roadmap hoped for "via
   Eureka where possible", but Prometheus has no native Eureka service
   discovery. The options were static targets, a file/http SD bridge, or a
   custom sidecar.
2. **Where do alerts go?** Prometheus can evaluate rules and nothing more;
   delivery needs Alertmanager and a receiver, and a local simulation has no
   on-call.

## Decision

**Discovery: a file_sd bridge generated from Eureka.** `infra/scripts/eureka-targets.mjs`
reads `/eureka/apps`, takes every `UP` instance (app name → `job`, port →
target) and writes `infra/observability/targets/zynema.json`; Prometheus
consumes it through `file_sd_configs` and hot-reloads on change. A committed
snapshot keeps `docker compose up` working without running the generator, and
`make refresh-targets` regenerates it.

- `file_sd` is a first-class Prometheus mechanism, and generating it from a
  registry without native SD is the standard bridge pattern (it is what
  operators do internally).
- The registry is the source of truth: adding a service means registering it,
  not editing Prometheus.
- **The generator is a development bridge, not the end state.** In production
  this file would be written by a controller watching the registry, or the
  bridge would not exist at all: on Kubernetes the answer is
  `kubernetes_sd_configs` / the Prometheus Operator (the repo already keeps
  `infra/k8s/` manifests), and a modern alternative for a Java/OTel stack is
  an OTel Collector owning the pipeline with Prometheus scraping or
  remote-writing through it. Eureka itself is in maintenance, so no new
  investment goes into it.
- Only Eureka's own metrics stay on a static target: the server does not
  register with itself, so it cannot come from the registry.

**Alert delivery: Alertmanager with email to MailHog.** Rules evaluate in
Prometheus; Alertmanager groups by `alertname` and `service` and delivers via
SMTP to MailHog (`mailhog:1025`), the sink the stack already runs for
notification-service. This makes the alert path testable end-to-end — fire an
alert, read the mail at `http://localhost:8025` — instead of a receiver that
goes nowhere. MailHog lives in the `core` profile: without it alerts still
evaluate and group (visible in Alertmanager's UI), only delivery fails.

**Signals reference each other.** Logs gain `[traceId,spanId]` from Micrometer's
MDC through the shared logging pattern; the Loki datasource turns the id into a
Tempo link (derived field) and Tempo links back to Loki; Tempo's
`metrics_generator` remote-writes span metrics and service graphs to
Prometheus (`--web.enable-remote-write-receiver`), so RED dashboards exist even
for spans whose services emit no HTTP metrics.

## Alternatives considered

- **Static targets only** — rejected: simplest, but it ignores the registry the
  platform already has and makes every new service a Prometheus edit.
- **A custom Eureka→`http_sd` sidecar** — rejected for this phase: correct for
  production at scale, but a new bespoke component to build, ship and secure
  for a single-node stack.
- **Rules without Alertmanager** — rejected: no grouping, silences or delivery;
  the "alerts" requirement would be half-met.
- **OTel Collector as the metrics pipeline now** — deferred: it is the likely
  long-term shape, but it is an architectural change (metrics transport for
  every service) that Fase 9 does not need; recorded here as the direction.

## Consequences

- Target staleness is possible between registry changes and
  `make refresh-targets`; the runbook documents when to run it.
- `promtool` in CI now validates config **and** rules, and `amtool` validates
  Alertmanager; the Prometheus CI step mounts the whole observability
  directory because `rule_files` resolve inside the container.
- MailHog (core profile) becomes a soft dependency of alert delivery, not of
  alert evaluation.
- Span metrics arrive through remote write: Prometheus must keep
  `--web.enable-remote-write-receiver` enabled, and Tempo's generator config is
  now part of the runtime contract.
