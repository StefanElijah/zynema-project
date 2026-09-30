# ADR-0029: The orchestrated saga, and when each style earns its keep

- **Status:** Accepted
- **Date:** 2026-09-29
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 7 (async messaging), commit 4

## Context

ADR-0007 promised both coordination styles for "subscription created →
onboarding", and ADR-0028 delivered the choreographed one. This commit builds
the orchestrated version so the comparison is honest — two working
implementations of the same flow, switchable — instead of an argument in a
document.

The flow has two steps after the subscription exists: grant the account the
`subscriber` role in the identity provider, then send the welcome email. The
role step can fail in ways the choreographed flow cannot see: if user-service
exhausts its retries, no event is ever emitted and the flow silently hangs.
That is exactly the class of problem orchestration is for.

## Decision

**An explicit state machine in `payment-service` drives the onboarding.**
Payment owns the subscription and the money, so it owns the flow's bookkeeping:
one row per run in `subscription_onboarding_sagas`, with the state
(`AWAITING_ROLE` → `AWAITING_NOTIFICATION` → `COMPLETED` / `COMPENSATED`), the
plan code, the notification id and the failure reason. "Where is subscription
X?" is a query.

- **The flow starts at a fact**: the orchestrator consumes payment's own
  `SubscriptionCreated` (claimed in `processed_events`) and sends
  `UserCommand.GrantRole` with a freshly minted saga id. Nothing calls the
  orchestrator internally; the event remains the trigger.
- **Commands leave through the outbox**, like events: the state change and the
  message are one transaction (ADR-0026), and the relay applies both styles of
  routing with the same machinery.
- **Replies are matched by id, never guessed.** The role reply carries the
  `sagaId` the command was sent with (the contract was completed to add it to
  `RoleGranted`/`RoleChangeFailed`/`RoleRevoked`); the notification reply
  carries the `notificationId` the saga minted. Every transition checks the
  expected state first, so a redelivery is a no-op.
- **A dead role command is answered**: user-service's `@DltHandler` turns an
  exhausted `GrantRole` into `RoleChangeFailed`, which is what lets the saga
  compensate instead of waiting forever.
- **Compensation is one explicit path**: `RoleChangeFailed` cancels the
  subscription (`CANCELED`, `canceledAt`) and publishes
  `SubscriptionCancelled` with the reason. A failed welcome email is
  deliberately _not_ compensated: the saga completes with a
  `failure_reason`, and the independent compensation reader (ADR-0028) records
  and exposes the notification failure while the customer keeps the access they
  paid for.
- **The role is granted through the Keycloak Admin API** by a machine account
  (`zynema-user-service`, `manage-users` + `view-realm`; the realm export adds
  the `subscriber` role and the service account). The reply says "the role is
  on the token now" because that is checkable: the integration test asserts a
  freshly issued token carries it.
- **One switch selects the style**: `zynema.saga.mode` (env `ZYNEMA_SAGA_MODE`,
  default `choreographed`). In `orchestrated` mode payment's trigger listener
  exists and notification's event-driven welcome listener does not; the command
  listeners always exist, because only the orchestrator sends those commands.

## When each style earns its keep

|                        | Choreographed (ADR-0028)            | Orchestrated (this ADR)                               |
| ---------------------- | ----------------------------------- | ----------------------------------------------------- |
| Coordinator            | none; each service reacts           | explicit state machine in payment                     |
| Where is a flow?       | read the topics                     | one row per run                                       |
| Adding/removing a step | touch every producer/consumer       | touch the orchestrator                                |
| Step fails silently    | possible (no reply at all)          | DLT handler emits the failure reply                   |
| Compensation           | local reaction to the failure event | an explicit transition the orchestrator owns          |
| Coupling               | low: services know events           | higher: services know commands                        |
| Best for               | one or two hops, local reactions    | flows that need waiting, timeouts, or a visible state |

The comparison is now a config flag, not an opinion: the same two steps, the
same contracts, both green in the suite.

## Consequences

**Positive**

- The failure mode that motivated the comparison is covered: a role command
  that cannot be applied anywhere ends as a `RoleChangeFailed` reply and a
  compensated subscription, with the reason stored on the saga.
- Commands and replies reuse the existing topics, outbox and typed container
  factories: orchestration added a state machine, not a new messaging stack.
- The saga is testable without Kafka for the happy transitions and with Kafka
  for the end-to-end ones; the tests play the other services by publishing the
  replies.

**Negative**

- Orchestration is more code than choreography: an entity, a table, a service
  and a listener per reply. Two styles to maintain is a real cost until one is
  deleted after the comparison.
- **Timeouts are not implemented**: a saga whose reply never arrives stays
  waiting forever. The documented escalation is a scheduled reaper that moves
  stale sagas to `COMPENSATED` with a timeout reason; the state column already
  makes that a small job.
- The compensation (cancel the subscription) is not an undo of the simulated
  charge; in a real PSP it would be a refund decision. The ADR-0007 rule stands:
  compensations are explicit code paths, not distributed rollbacks.

## Notes

- `SubscriptionOnboardingOrchestrator` is the only writer of the saga table;
  the listeners only translate Kafka records into its methods.
- The saga's commands are at-least-once like every outbox message; idempotency
  is the state check plus Keycloak's naturally idempotent grant.
- The `zynema-user-service` machine secret is a development default, like the
  other clients in the realm export.
