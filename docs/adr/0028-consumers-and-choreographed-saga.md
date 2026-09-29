# ADR-0028: Consumers, dead letters and the choreographed compensation

- **Status:** Accepted
- **Date:** 2026-09-29
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 7 (async messaging), commit 3

## Context

ADR-0025 fixed the contracts and ADR-0026 the producer side; ADR-0007 promised
both a choreographed and an orchestrated version of "subscription created →
welcome email". This commit builds the first set of consumers and closes the
choreographed loop: notification answers a subscription with an email, and when
it cannot, payment has to know.

Two things had to be decided that the earlier ADRs only sketched: where the
declared domain of a listener lives so that **retries** still deserialise, and
what exactly "compensation" means for a paid subscription whose email failed.

## Decision

**The consumed domain is declared on the container factory, not on the
listener.** ADR-0025 documented `@KafkaListener(properties =
"json.value.type=…")`. That works for the first delivery and fails for the
retry topics: the retry machinery builds its containers from the endpoint's
container factory, and per-listener properties are not reapplied there. A retry
then deserialises into a map and the handler silently stops matching its event
type. Each consumed domain therefore gets a
`ConcurrentKafkaListenerContainerFactory` whose consumer factory carries the
declared type (`ListenerConfig` in notification and payment). The type stays
per-domain, which was the point of the original decision.

**Notification is a projection plus an outbox.** `contacts` is fed from
`UserEvent.UserRegistered` — the only event that carries an email; commands and
payment events never do. `subscription-welcome` is rendered from the template
registry and sent through MailHog in development. The consumer claims the
event, sends, then writes `notification_log` (SENT) and appends
`NotificationSent` to the outbox **in one transaction**. Email is
at-least-once: a crash after the send duplicates the message, while the log and
the acknowledgement exist if and only if the transaction commits.

**A poison pill is visible.** After `@RetryableTopic` exhausts its attempts,
the `-dlt` handler persists the record in `notification_dead_letters` (unique
per topic/partition/offset) and logs it. When the dead-lettered event was a
`payment.subscription-created`, it also records a FAILED log row and appends
`NotificationFailed`, reusing the source event's id as the notification id so
the compensation is idempotent by construction.

**Compensation means record and expose, never reverse.** Payment consumes
`NotificationFailed` with its own `ProcessedEventStore` claim and writes
`subscription_notification_failures` (primary key: the notification id). The
subscription keeps its status and the payment stands; the failure is exposed in
`GET /api/v1/payments/subscriptions/me` as a `notificationFailure` object, and
`SubscriptionNotificationFailed` leaves through payment's existing outbox as
the platform's record of the accepted compensation.

## Consequences

**Positive**

- The full choreography is exercised end to end in tests: real broker, real
  registry, real MailHog. The happy path asserts the mailbox through MailHog's
  API; the failure path asserts the persisted dead letter, the FAILED log and
  the compensating event.
- `processed_events` plus the natural keys (notification id in payment, the
  contact upsert, the log's `ON CONFLICT DO NOTHING`) make every consumer safe
  against the at-least-once redeliveries the outbox promises.
- Payment's decision is small, auditable and reversible by a human: customers
  never lose access because a mailbox bounced.

**Negative**

- A duplicated email is possible (crash between send and commit). Accepted:
  the alternative — commit first, send later — either loses the email or needs
  a second outbox-of-emails mechanism.
- The dead-letter handler runs on the `-dlt` container with a payload that may
  no longer be typed; the compensation extracts `userId` defensively from
  typed records, maps, `JsonNode`, JSON text or bytes. It is tested, but it is
  a symptom of two serialisation paths (main and retry topics) that a future
  refactor should unify.
- `subscription_notification_failures` has no cleanup policy; the
  `notification_log` and `notification_dead_letters` tables are append-only,
  so the same partition/TTL job the other services still owe applies here.

## Notes

- The producer side of the saga is user-service: JIT provisioning appends
  `UserRegistered` through the shared outbox, in the same transaction as the
  account row.
- MailHog joined the `core` compose profile: running the saga without a mailbox
  would only produce dead letters.
- The orchestrated version of the same flow is still pending; the contracts
  (`NotificationCommand`, command topics) already exist for it.
