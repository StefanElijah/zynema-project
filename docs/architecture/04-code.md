# C4 — Level 4: Code (selected)

Level 4 shows specific classes for the parts where the code alone does not
tell the story. Everything else is the code itself.

The four non-obvious pieces of this platform, and where they live:

| Piece                                     | Where it is explained                                                               |
| ----------------------------------------- | ----------------------------------------------------------------------------------- |
| **Event sourcing** in playback-service    | [`docs/learning/event-sourcing.md`](../learning/event-sourcing.md) (ADR-0027)       |
| **Saga** (choreographed and orchestrated) | [`docs/learning/saga-pattern.md`](../learning/saga-pattern.md) (ADR-0028, ADR-0029) |
| **Outbox** relay loop                     | [ADR-0026](../adr/0026-shared-outbox.md): recorder, relay, serializer               |
| **BFF** composition and resilience        | [ADR-0021](../adr/0021-bff-composition.md) and the component diagram in 03          |
| **Generated SPA client**                  | [ADR-0030](../adr/0030-generated-spa-client.md): orval, mutator, snapshot           |

Key classes, as a map rather than a diagram:

- `playback`: `SessionEventStore`, `SessionState` (the fold), `SessionProjection`,
  `PlaybackService` — append event + outbox row + projection in one transaction.
- `common.messaging`: `OutboxRecorder`, `OutboxRelay`, `EventPublisher`,
  `ProcessedEventStore` — the shared async plumbing every service reuses.
- `payment`: `SubscriptionService`, `SagaOrchestrator`, `NotificationCompensationService`.
- `bff`: `UpstreamGateway` (all downstream calls), `ViewMapper`, the controllers.
- `libs/api-contracts`: the orval-generated client and the axios mutator.
