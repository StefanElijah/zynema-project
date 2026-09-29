# Saga pattern — practical notes

How the "subscription created → welcome email" flow is coordinated in Zynema
(ADR-0007, ADR-0025, ADR-0026, ADR-0028). Two styles are promised by the
roadmap; the **choreographed** one is implemented in Fase 7.

## Choreographed: no coordinator, the events are the plan

```
payment-service                notification-service                payment-service
     │                                  │                                │
     │ SubscriptionCreated ───────────► │ claims, projects contact,      │
     │ (outbox, same tx as the write)   │ sends the email, logs SENT,    │
     │                                  │ appends NotificationSent ─────►│ (ack, no action)
     │                                  │                                │
     │                                  │ retries exhausted              │
     │                                  │ dead letter persisted          │
     │                                  │ NotificationFailed ───────────►│ claims, records failure,
     │                                  │                                │ exposes it, emits
     │                                  │                                │ SubscriptionNotificationFailed
```

Nobody drives this: each service reacts to the previous event. What keeps it
honest is that the arrows are **facts** (events), not requests, and each hop
carries an id so every consumer can deduplicate.

## Orchestrated: a coordinator tells each step what to do

The same flow also runs orchestrated (ADR-0029), driven by
`SubscriptionOnboardingOrchestrator` in payment-service:

```
SubscriptionCreated ──► saga row: AWAITING_ROLE
                             │ GrantRole (user.commands)
                             ▼
                        user-service ──► Keycloak Admin API
                             │ RoleGranted / RoleChangeFailed (user.events)
                             ▼
                        saga row: AWAITING_NOTIFICATION
                             │ SendNotification (notification.commands)
                             ▼
                        notification-service ──► email
                             │ NotificationSent / NotificationFailed
                             ▼
                        saga row: COMPLETED / COMPENSATED
```

What makes it orchestration and not choreography:

- **One state machine owns the flow.** The saga row is the place where "where
  is subscription X?" is answered; the services only obey commands and reply.
- **The replies are matched by id**: the command carried the `sagaId` (roles)
  or the saga minted the `notificationId` (email). No guessing from user ids.
- **Nothing fails silently.** A role command that exhausts its retries becomes
  a `RoleChangeFailed` reply from the dead-letter handler, so the orchestrator
  can compensate instead of waiting forever.
- **The switch is config**: `zynema.saga.mode=choreographed|orchestrated`
  decides who acts; both implementations share the contracts, topics, outbox
  and typed listener factories.

Rule of thumb, after building both: choreographed for one or two hops with
local reactions; orchestrated once steps can fail silently, need timeouts, or
someone will ask where a flow is.

## Compensation is a new fact, not an undo

The failed welcome email does not roll anything back:

- the subscription stays ACTIVE and the payment stands — the customer paid for
  access and received it;
- payment records `subscription_notification_failures` and exposes it on the
  subscription, so the client and support can see what happened;
- `SubscriptionNotificationFailed` is emitted so the rest of the platform can
  react to the accepted state.

Trying to "undo" the subscription would be a distributed transaction by
another name, and a mailbox problem is not a reason to take away access.

## The mechanics that make it safe

| Problem                             | Mechanism                                             | Where                                  |
| ----------------------------------- | ----------------------------------------------------- | -------------------------------------- |
| Duplicate delivery                  | `processed_events` claim in the handler's transaction | `ProcessedEventStore`                  |
| Event lost between write and broker | transactional outbox, same tx as the state            | `OutboxRecorder` / `OutboxRelay`       |
| Poison pill                         | `@RetryableTopic` then a persisted dead letter        | `notification_dead_letters`            |
| Email sent twice                    | accepted: mail is at-least-once                       | log + ack only exist if the tx commits |
| The email is unknown                | contact projection from `UserRegistered`              | `contacts`                             |
| Retries that lose the event type    | declared type on the container factory                | `ListenerConfig`                       |

## Testing a saga

The tests install the same infrastructure the platform runs: a real broker, a
real Schema Registry and a real MailHog. The happy path asserts the mailbox
through MailHog's HTTP API; the failure path publishes a subscription for a
user that never registered, lets the retries exhaust and asserts the dead
letter, the FAILED log and the compensating event. See
`NotificationFlowTests` and `NotificationFailureTests`.
