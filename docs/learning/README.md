# Learning notes

Deep dives on the patterns we use. Not required to operate the system,
but useful to understand **why** the code looks the way it does.

| Topic                                       | File                                                   | Status |
| ------------------------------------------- | ------------------------------------------------------ | ------ |
| Microservices patterns overview             | [microservices-patterns.md](microservices-patterns.md) | Stub   |
| Distributed tracing (OTel + Tempo)          | [distributed-tracing.md](distributed-tracing.md)       | Stub   |
| Saga pattern (choreographed + orchestrated) | [saga-pattern.md](saga-pattern.md)                     | Stub   |
| Outbox pattern                              | [outbox-pattern.md](outbox-pattern.md)                 | Stub   |
| CQRS — practical notes                      | [cqrs.md](cqrs.md)                                     | Stub   |
| Event Sourcing — practical notes            | [event-sourcing.md](event-sourcing.md)                 | Ready  |
| Circuit Breaker / Resilience4j              | [resilience4j.md](resilience4j.md)                     | Stub   |
| Idempotency Keys                            | [idempotency.md](idempotency.md)                       | Stub   |
| Rate Limiting with Bucket4j                 | [rate-limiting.md](rate-limiting.md)                   | Stub   |
| Database per service                        | [database-per-service.md](database-per-service.md)     | Stub   |

Each file is filled in **as the corresponding phase is implemented**.
The stubs above are intentional: writing them before the code is
premature, and writing them after the code is easy.
