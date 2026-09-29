# ADR-0025: Topics by aggregate, schemas per record, metadata in headers

- **Status:** Accepted
- **Date:** 2026-09-29
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 7 (async messaging)

## Context

Fase 7 turns the platform asynchronous: payment and playback publish events,
notification consumes them and sends email, and two coordination styles
(choreographed and orchestrated) drive the same subscription flow. That needs
three decisions that are expensive to change later: how topics are laid out,
what the registry actually checks, and where the envelope lives.

## Decision

**One topic per aggregate** (`zynema.payment.events`, `zynema.user.events`,
`zynema.playback.events`, `zynema.notification.events`), with the **aggregate
id as the key** so one aggregate's events stay ordered within a partition.
Commands get their own topics (`zynema.user.commands`,
`zynema.notification.commands`): an event says "this happened" and can have many
readers; a command says "do this" and has exactly one logical executor. Mixing
them makes both statements untrue.

**The payload is the message value; the envelope lives in headers.** Each event
record therefore gets its **own JSON Schema**, registered under
`zynema.payment.events-<RecordName>` (`TopicRecordNameStrategy`), and
compatibility BACKWARD genuinely protects the payload. Wrapping everything in
one generic envelope on the wire was the first attempt and had to be undone: the
registry saw the _envelope_ — one permissive subject per topic, in which any
payload change was "compatible". What travels in headers:

| Header          | Meaning                                                     |
| --------------- | ----------------------------------------------------------- |
| `eventId`       | idempotency key for consumers                               |
| `eventType`     | derived from topic + class (`payment.subscription-created`) |
| `eventSource`   | `spring.application.name` of the producer                   |
| `eventTime`     | when it happened, not when it was published                 |
| `eventVersion`  | contract version (`1.0`)                                    |
| `correlationId` | ties one flow together (saga, request)                      |

**Payload types are resolved by the domain interface, not by a registry in
code.** `@JsonTypeInfo`/`@JsonSubTypes` on the sealed interfaces
(`PaymentEvent`, …) make each record self-describing, and a listener declares
the domain it speaks: `json.value.type=dev.zynema.events.PaymentEvent` per
`@KafkaListener(properties = …)`. Jackson then resolves the concrete record.
`EventTypes` keeps the type→class map for tooling, tests and the derivation
itself, so producer and consumer cannot drift.

**Tracing does not travel in the envelope.** The Kafka observation
instrumentation propagates `traceparent` in headers by itself; a second copy in
the envelope would be two sources of truth for one fact.

**Failure handling is declarative**: `@RetryableTopic` with exponential backoff
and a dead-letter topic per consumer (`<topic>-dlt`), configured once in the
shared config repo. A message that keeps failing is a bug or a poison pill, and
neither should block a partition forever.

## Consequences

**Positive**

- The registry is load-bearing: a breaking change to a record fails the
  producer before it can publish, which is what "schema contracts" is supposed
  to mean.
- Adding an event type is an entry in `EventTypes`, `@JsonSubTypes` and a
  consumer — no new topic, no new DLQ, no re-partitioning.
- Consumers are idempotent by construction: `eventId` is in every message and
  `ProcessedEventStore` claims it in the consumer's own transaction.

**Negative**

- Two places to keep in step: the Jackson annotations and `EventTypes` (a test
  asserts they agree for every registered message).
- `json.value.type` must be declared per listener; a listener that forgets it
  receives a map and fails at the first access.
- Confluent's clients are not on Maven Central, so the parent POM declares their
  repository, and their version tracks the registry image (7.6.1) — both
  documented in the POM next to the property.

## Notes

- **Test infrastructure**: `ConfluentKafkaContainer` with a second listener on
  the Docker network alias, plus the registry on the same network pointing at
  it. The first attempt used `host.docker.internal` from the registry to the
  broker's mapped port and timed out; the network alias is the supported path.
- **Ports**: Kafka advertises the host listener on **29092** (9092 is
  in-network), and the registry is published on **8088** because 8085 belongs
  to playback-service.
- Kafka, the registry and topic creation run in the `core` profile;
  `kafka-init` creates the six topics explicitly so they exist (and are
  visible) before any service starts.
