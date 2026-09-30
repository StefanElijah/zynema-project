# Runbook — Observability

How the three signals are wired, how to verify them, and what to check when
something is missing. The architecture decisions behind this are in
[ADR-0031](../adr/0031-observability-wiring.md).

## Entry points

| What            | Where                           | Notes                                                                  |
| --------------- | ------------------------------- | ---------------------------------------------------------------------- |
| Grafana         | http://localhost:3000           | admin / `GRAFANA_ADMIN_PASSWORD` in `.env`                             |
| Dashboards      | Grafana → folder **Zynema**     | provisioned from git, not editable in the UI                           |
| Prometheus      | http://localhost:9090           | targets, rules, `ALERTS`, remote-write                                 |
| Alertmanager    | http://localhost:9093           | grouping, silences                                                     |
| Alert mail      | http://localhost:8025 (MailHog) | the same sink as notification-service                                  |
| Loki (via API)  | http://localhost:3100           | `curl '.../loki/api/v1/query_range?query={service="catalog-service"}'` |
| Tempo (via API) | http://localhost:3200           | search lags; prefer fetching a trace by id                             |

Start it with `make up-observability` (and `make up-core` / `make up` for the
services whose metrics you expect). The command refreshes the scrape targets
from Eureka when Eureka is already up.

## How it is wired

- **Targets**: `make refresh-targets` reads Eureka and regenerates
  `infra/observability/targets/zynema.json`; Prometheus hot-reloads it
  (`file_sd`). The `job` label is the application name.
- **Logs**: Promtail discovers containers through the Docker socket and labels
  them with `service` = compose service name (the same identity as the
  Prometheus `job` and the Tempo `service.name`). The shared log pattern
  prints `[traceId,spanId]`.
- **Traces**: services export OTLP to Tempo; Tempo's metrics generator
  remote-writes span metrics and service graphs to Prometheus.
- **Alerts**: rules in `prometheus-rules.yml` → Alertmanager → email to
  MailHog.

## Verification checklist

```bash
# 1. Targets: every service UP (also covers the discovery wiring)
curl -s localhost:9090/api/v1/targets | jq -r '.data.activeTargets[] | "\(.labels.job) \(.health)"'

# 2. Rules loaded and evaluated
curl -s localhost:9090/api/v1/rules | jq -r '.data.groups[].rules[].name'

# 3. Grafana provisioned (datasources + dashboards inside the container)
curl -s -u admin:$GRAFANA_ADMIN_PASSWORD localhost:3000/api/datasources | jq -r '.[].name'
curl -s -u admin:$GRAFANA_ADMIN_PASSWORD 'localhost:3000/api/search?query=Zynema' | jq -r '.[].title'

# 4. Logs carry the service label and a trace id
curl -s 'localhost:3100/loki/api/v1/query_range?query={service="catalog-service"}&limit=1' | jq -r '.data.result[0].values[0][1]'

# 5. Span metrics arrived through remote write
curl -s 'localhost:9090/api/v1/query?query=sum(rate(traces_spanmetrics_calls_total[5m]))' | jq '.data.result[0].value[1]'

# 6. A trace, by the id a log line printed (Tempo search lags behind; ids do not)
curl -s localhost:3200/api/traces/<traceId> | jq '.batches | length'
```

### Testing an alert end to end

```bash
docker stop zynema-auth           # any service works
# wait ~2-3 minutes: rule fires, Alertmanager groups, MailHog receives
curl -s 'localhost:9090/api/v1/query?query=ALERTS{alertname="ZynemaServiceDown"}' | jq '.data.result'
curl -s 'localhost:8025/api/v2/messages' | jq -r '.items[].Content.Headers.Subject[0]'
docker start zynema-auth          # resolution mail follows (send_resolved: true)
```

Prometheus's own UI (Alerts tab) and Alertmanager (`:9093`) show the same
firing/resolved transitions without MailHog, so the check works even when the
core profile (MailHog) is not up.

## Troubleshooting

| Symptom                                  | Likely cause and fix                                                                                                                                          |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| A service is missing from targets        | It is not registered in Eureka, or the file is stale: `make refresh-targets` and check `/targets`                                                             |
| A target is DOWN but the service answers | The target still points at an old container IP after a rebuild: `make refresh-targets`                                                                        |
| Alerts fire for services that are up     | After a rebuild the old instance series take ~5 minutes to go stale; refresh the targets and wait. If it repeats, check `refresh_interval` in the file_sd job |
| No logs in Loki for a service            | Promtail needs the Docker socket; check `docker logs zynema-promtail` for permission errors                                                                   |
| Log lines show `[,]` instead of ids      | The logger ran outside a span scope (startup, background threads): legitimate. The trace→logs direction in Grafana does not depend on it                      |
| A trace has no linked logs in Grafana    | The derived field matches `[traceId,spanId]`; a line without the pattern cannot link                                                                          |
| Tempo search returns nothing             | Search reads completed blocks (~5 min). Verify with a trace id from a log line instead                                                                        |
| No `traces_spanmetrics_*` series         | Prometheus needs `--web.enable-remote-write-receiver` (compose command) and Tempo's generator config                                                          |
| Alert fires but no mail                  | MailHog is in the core profile: `make up-core`; check `docker logs zynema-alertmanager`                                                                       |
| Grafana has no datasources/dashboards    | Provisioning comes from git: check the container logs for "provisioning" errors and the mounted paths                                                         |
| `amtool`/`promtool` fails in CI          | Validate locally: `promtool check config/rules` and `amtool check-config` over `infra/observability` — the images need `--entrypoint`                         |
