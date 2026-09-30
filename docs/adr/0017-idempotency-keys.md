# ADR-0017: Idempotency keys for operations that move money

- **Status:** Accepted
- **Date:** 2026-09-27
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 4 (domain services and resilience)

## Context

Creating a subscription charges the account. Networks retry, users double-tap,
and a client that times out has no way of knowing whether the charge happened.
Making the endpoint `POST` with a unique key in the body does not fix it: the
client would have to invent a second key, which is the same problem.

## Decision

Every state-changing billing endpoint requires an `Idempotency-Key` header and
is idempotent by contract:

1. **Reserve before executing.** The key row is inserted with status
   `IN_PROGRESS` in its own committed transaction _before_ the business logic
   runs. A concurrent duplicate therefore finds the reservation. A
   lookup-then-execute design cannot promise this: both requests miss the
   lookup and both charge.
2. **Hash the payload.** The same key with a different body is a client bug and
   answers `409 Conflict` instead of silently returning the previous result.
3. **Replay the stored response.** A completed key returns the original status
   and body, so a retry is indistinguishable from the first call.
4. **Release on failure.** A failed attempt deletes its reservation, so a
   corrected retry with the same key works instead of being told "still
   processing" forever. Keys expire after 24 hours.

The orchestration is deliberately **not transactional**: the key is reserved,
then the business transaction commits, and only then is the key completed.
Wrapping all three in one transaction would let a duplicate read a completed key
whose business data had not committed yet.

## Consequences

**Positive**

- A retried request can never charge twice; the guarantee lives in the database
  (`key` is the primary key) instead of in application code.
- The conflict cases are explicit: same key + different payload (409), or a
  concurrent duplicate (409).
- The same building block generalises to any non-idempotent operation (payments,
  refunds, transcoding jobs).

**Negative**

- One extra table and one extra round of writes per billable operation.
- Clients must send the header; the API answers `400` when it is missing, which
  is a contract their HTTP library has to support.
- The reservation/complete split needs two extra transactions, so it is worth it
  only where a duplicate has a real cost.

## Notes

- A generic filter in `common` was considered and rejected for now: buffering and
  replaying arbitrary responses is more error-prone than an explicit service
  implementation. It stays as a candidate refactor once a second service needs
  the same behaviour.
- Verified by `SubscriptionApiTests`: replay returns the identical body with a
  single subscription and a single payment, same key with another payload is a
  409, and a failed attempt frees the key.
