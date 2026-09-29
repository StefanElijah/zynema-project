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

For flows whose compensation needs timeouts, retries across several services
and a place to ask "where is flow X?", the plan is an orchestrator that sends
**commands** (`SendNotification`) and waits for replies (`NotificationSent` /
`RoleChangeFailed`). The contracts already exist (`NotificationCommand`, the
command topics), but the state machine does not; the comparison is the pending
item in Fase 7.

Rule of thumb: choreographed for one or two hops with local reactions;
orchestrated once you need to see the flow in one place.

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
