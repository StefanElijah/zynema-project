# Five-minute demo script

A script for showing Zynema end to end. Timings assume a warm machine; the
stack takes a couple of minutes to come up the first time.

## 0. Bring the stack up (once, beforehand)

```bash
make up                      # core + auth: services, Keycloak, MailHog
make up-observability        # Prometheus, Grafana, Loki, Tempo, Alertmanager
# optional, only for real video playback:
make up-storage && make fetch-samples && make transcode
```

Open the tabs you will use: **http://localhost:5173** (SPA),
**http://localhost:3000** (Grafana, admin/`GRAFANA_ADMIN_PASSWORD`),
**http://localhost:8025** (MailHog), **http://localhost:9001** (MinIO console).

## 1. The product, as a visitor (60 s)

1. `http://localhost:5173` — the landing page is composed by the BFF:
   hero + rails. Open **Series** and **Películas** from the navbar: the
   filters live in the URL, the grid is the catalogue's read model.
2. Search “arcane” in the navbar.
3. Open a title: the detail page shows metadata, seasons, credits — and the
   **call to action the BFF decided for this visitor** (anonymous →
   “Iniciar sesión para ver”; no plan → “Necesitás un plan”).

## 2. The product, as a subscriber (90 s)

1. **Iniciar sesión** → `demo` / `demo` (Keycloak, PKCE).
2. The navbar now has the profile switcher; pick “Demo” (or “Kids”).
3. **Planes** → subscribe to _Standard_. Point out:
   - the SPA sends an `Idempotency-Key`; retrying the same intent returns
     the original subscription instead of charging twice (ADR-0017);
   - a subscription event left through the outbox (ADR-0026) and the
     choreographed saga sent the welcome email — check **MailHog**.
4. **Mi Lista** → add the title from a card; it appears in the rails.
5. Play: **▶** on the detail page. The player uses the custom controls
   (seek, volume, fullscreen), heartbeats every 15 s and closes the session
   on the way out. The manifest comes from playback, the segments are
   signed with a 60 s TTL and proxied by the nginx edge (ADR-0024).
6. Without the storage profile the play attempt answers
   `409 CONTENT_NOT_READY` — the same flow, explained by the UI.
7. **Mi cuenta**: identity, subscription and profile CRUD (create a profile
   with the Zod-validated form).

## 3. The platform behind it (90 s)

1. **Grafana → Zynema — Overview**: services up, RPS, error rate, p99, heap.
2. **Zynema — Service** → pick `bff-service`: RED panels, JVM, breakers,
   **span metrics and the service graph** (Tempo remote-writes them), and
   its recent error logs from Loki.
3. In the logs, click a `traceId` → the trace opens in **Tempo**; from the
   trace, “logs for this span” comes back to Loki.
4. **Alerts, live**: `docker stop zynema-auth`; in ~2 minutes
   `ZynemaServiceDown` fires, **MailHog** receives the alert and Alertmanager
   shows the group. `docker start zynema-auth` resolves it.
5. **Kafka**: `make kafka-topics` lists the aggregate topics; subscribe in
   the app and watch `zynema.payment.events` carry `SubscriptionCreated`.

## 4. Engineering talking points (30 s)

- `pnpm api:generate` regenerates the typed SPA client from the committed
  BFF snapshot; CI fails if the snapshot drifts from the live document.
- Every CI workflow is green: backend in three parallel groups, frontend
  matrices (Node LTS, per-browser e2e), observability config validation and
  the SonarCloud gate.
- `mvn -B verify` runs 300+ tests, including Testcontainers-backed
  integration tests and the event-sourced session suite.
