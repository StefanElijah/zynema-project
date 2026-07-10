# C4 — Level 3: Components (selected)

This level is fleshed out **per service** as we implement them. Below is the
generic shape that all services follow. Use the service-specific docs in
`backend/<service>/README.md` (when added) for the unique pieces.

## Generic microservice shape

```mermaid
C4Component
    title Component Diagram — Generic Microservice

    Container_Boundary(svc, "Service") {
        Component(api, "REST API", "Spring MVC controllers", "HTTP endpoints, DTOs, validation")
        Component(svc_layer, "Application Service", "@Service", "Use cases, orchestration, transactions")
        Component(domain, "Domain Model", "JPA entities + value objects", "Business invariants")
        Component(repo, "Repository", "Spring Data JPA", "Persistence")
        Component(events, "Event Publisher", "Spring Cloud Stream", "Publish domain events")
        Component(client, "Outbound Client", "OpenFeign", "Call other services")
        Component(config, "Config", "@ConfigurationProperties", "Typed config beans")
    }
    ContainerDb(db, "DB", "PostgreSQL", "")
    Container_Ext(bus, "Kafka", "Event bus", "")
    Container_Ext(other, "Other Service", "Downstream", "")

    Rel(api, svc_layer, "Delegates")
    Rel(svc_layer, domain, "Manipulates")
    Rel(svc_layer, repo, "Reads/writes")
    Rel(repo, db, "SQL")
    Rel(svc_layer, events, "Publishes")
    Rel(events, bus, "Kafka")
    Rel(svc_layer, client, "Calls")
    Rel(client, other, "HTTP")
```

## Patterns used across services

| Concern | Where it lives |
|---|---|
| HTTP entry point | `api` package |
| Use cases | `service` or `application` package |
| Domain rules | `domain` package (entities + value objects) |
| Persistence | `repository` package (Spring Data) |
| Outbound HTTP | `client` package (OpenFeign interfaces) |
| Outbound events | `events` package (Stream producers) |
| Cross-cutting config | `config` package |

## Service-specific component diagrams (TODO)

- [ ] catalog-service
- [ ] payment-service
- [ ] playback-service
- [ ] bff-service
- [ ] user-service
- [ ] auth-service
- [ ] notification-service
