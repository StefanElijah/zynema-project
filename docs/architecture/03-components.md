# C4 — Level 3: Components (selected)

Most services follow the same shape; the non-obvious ones get their own
diagram. The learning notes go deeper where the pattern deserves it
(`docs/learning/event-sourcing.md`, `docs/learning/saga-pattern.md`).

## Generic microservice shape

```mermaid
C4Component
    title Component Diagram — Generic Microservice

    Container_Boundary(svc, "Service") {
        Component(api, "REST API", "Spring MVC controllers", "HTTP endpoints, DTOs, validation")
        Component(svc_layer, "Application Service", "@Service", "Use cases, orchestration, transactions")
        Component(domain, "Domain Model", "JPA entities + value objects", "Business invariants")
        Component(repo, "Repository", "Spring Data JPA", "Persistence")
        Component(outbox, "Outbox + Relay", "JDBC + scheduler", "Events committed with the write (ADR-0026)")
        Component(client, "Outbound Client", "OpenFeign", "Call other services")
        Component(resilience, "Resilience", "Resilience4j", "Retry, breaker, bulkhead")
        Component(security, "Security", "Shared starter", "JWT validation, roles (ADR-0014)")
    }
    ContainerDb(db, "DB", "PostgreSQL", "")
    Container_Ext(bus, "Kafka", "Event bus", "")
    Container_Ext(other, "Other Service", "Downstream", "")

    Rel(api, security, "Runs behind")
    Rel(api, svc_layer, "Delegates")
    Rel(svc_layer, domain, "Manipulates")
    Rel(svc_layer, repo, "Reads/writes")
    Rel(repo, db, "SQL")
    Rel(svc_layer, outbox, "Records events in the transaction")
    Rel(outbox, bus, "Publishes", "Kafka")
    Rel(svc_layer, client, "Calls")
    Rel(client, resilience, "Wrapped by")
    Rel(client, other, "HTTP")
```

## BFF (the SPA's only surface)

```mermaid
C4Component
    title Component Diagram — bff-service

    Container_Ext(spa, "Web SPA", "React + generated client")
    Container_Boundary(bff, "bff-service") {
        Component(controllers, "Web controllers", "@RestController", "One endpoint per screen under /api/v1/web/**")
        Component(gatewayService, "UpstreamGateway", "@Service", "Every downstream call and its resilience policy")
        Component(clients, "WebClients", "WebClient", "catalog, user, payment, playback")
        Component(viewMapper, "ViewMapper", "Plain Java", "Downstream records to screen view models")
        Component(cache, "Screen cache", "Redis + AOP", "Whole screens with per-cache TTLs (ADR-0021)")
        Component(resilience, "Resilience4j", "AOP", "Retry > breaker > bulkhead, per dependency")
        Component(relay, "Token relay", "WebFilter", "Forwards the caller's bearer token and correlation id")
    }
    Container_Ext(downstream, "Domain services", "catalog, user, payment, playback")

    Rel(spa, controllers, "HTTPS")
    Rel(relay, controllers, "Authenticates")
    Rel(controllers, gatewayService, "Calls")
    Rel(gatewayService, resilience, "Wrapped by")
    Rel(gatewayService, clients, "Uses")
    Rel(gatewayService, cache, "Cached by (outside the aspects)")
    Rel(clients, downstream, "WebClient")
    Rel(controllers, viewMapper, "Maps with")
```

## Payment: outbox, saga and the read side

```mermaid
C4Component
    title Component Diagram — payment-service (async)

    Container_Boundary(payment, "payment-service") {
        Component(subscriptions, "SubscriptionService", "@Service", "Subscribe/cancel; records events in the transaction")
        Component(outbox, "OutboxRecorder + OutboxRelay", "JDBC + scheduler", "Shared component (ADR-0026)")
        Component(listeners, "Event consumers", "@KafkaListener", "NotificationFailed, UserRegistered, saga replies")
        Component(orchestrator, "Saga orchestrator", "@Service", "Explicit state machine; commands + timeouts (ADR-0029)")
        Component(compensation, "Compensation", "@Service", "Reverses the role step without moving money")
    }
    ContainerDb(db, "Payment DB", "PostgreSQL", "subscriptions, outbox, saga_instance")
    Container_Ext(bus, "Kafka", "Events + commands", "")
    Container_Ext(keycloak, "Keycloak", "Realm roles", "")

    Rel(subscriptions, outbox, "Records SubscriptionCreated / PaymentSucceeded")
    Rel(outbox, bus, "Publishes", "Kafka")
    Rel(bus, listeners, "Delivers", "Kafka")
    Rel(listeners, orchestrator, "Drives")
    Rel(orchestrator, bus, "Commands (GrantRole, SendNotification)")
    Rel(listeners, compensation, "NotificationFailed triggers")
    Rel(orchestrator, keycloak, "Indirectly, through user-service")
    Rel(orchestrator, db, "Persists saga_instance")
```

## Service-specific component diagrams

- [x] bff-service (above)
- [x] payment-service (async paths, above)
- [x] playback-service — the event-sourced session lives in
      [`docs/learning/event-sourcing.md`](../learning/event-sourcing.md)
      (append-only log, fold, snapshots, projection)
- [x] the saga styles live in
      [`docs/learning/saga-pattern.md`](../learning/saga-pattern.md) and
      [ADR-0029](../adr/0029-orchestrated-saga-comparison.md)
- [ ] catalog-service, user-service, auth-service, notification-service:
      the generic shape above is their diagram; the unique pieces are the read
      model projection (ADR-0022), the Keycloak admin client and the contacts
      projection (ADR-0028) respectively.
