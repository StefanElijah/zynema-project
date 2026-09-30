# ADR-0013: Typed JSON cache values, never Jackson default typing

- **Status:** Accepted
- **Date:** 2026-09-23
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 2 (persistence and first domain)

## Context

Catalog reads are cache-aside cached in Redis. The cache has to serialise DTOs
that are Java records, and a record is `final`, so a serialiser cannot infer
the concrete type when deserialising into `Object`.

The common shortcuts are:

1. **JDK serialisation** (`JdkSerializationRedisSerializer`, Spring Boot's
   default). Requires `Serializable` DTOs, stores opaque binary, and breaks on
   any DTO refactor or package move. Cached entries become unreadable — the
   opposite of an operational asset.
2. **Jackson default typing** (`GenericJackson2JsonRedisSerializer` with
   `activateDefaultTyping`). Writes `@class` type hints into the payload so
   records round-trip. This is the classic **deserialisation gadget** setup:
   whoever can write to Redis can name the class Jackson instantiates. It also
   trips `DefaultTyping.EVERYTHING` deprecation in Jackson 2.19.

## Decision

Every cache declares the exact type it stores, and values are serialised as
plain JSON **without** type hints:

```java
RedisCacheManager.builder(connectionFactory)
    .withCacheConfiguration(CONTENT_DETAIL, jsonCache(mapper,
        types.constructType(ContentDetailDto.class), ttl, prefix))
    .withCacheConfiguration(CONTENT_LIST, jsonCache(mapper,
        types.constructParametricType(PageResponse.class, ContentSummaryDto.class), ttl, prefix))
    ...
    .disableCreateOnMissingCache()
    .build();
```

`disableCreateOnMissingCache()` makes an unregistered cache name a startup-time
mistake instead of a silent cache with default (wrong) serialisation.

Caching is declared on the **public entry points** (`listMovies`,
`listSeries`, `search`, `getBySlug`, `listEpisodes`, `listGenres`), never on a
method reached only through self-invocation — Spring AOP does not apply advice
to internal calls, so a `@Cacheable` on an internally-called method is a no-op.

Writes do not read through the cache: `CatalogCommandService` builds its
response with the non-cached `buildDetail(...)` and evicts the catalog caches
after the mutation. Reading through `getBySlug` inside a write would serve the
stale entry that has not been evicted yet.

## Consequences

**Positive**

- Cached payloads are human-readable JSON and survive DTO refactors.
- No polymorphic deserialisation surface: an attacker with Redis write access
  cannot name arbitrary classes.
- Cache misses after a schema change are just misses, not exceptions.

**Negative**

- Every new cache must be registered with its type (one line) — a deliberate,
  discoverable cost.
- Generic payloads need an explicit `JavaType`
  (`constructParametricType`, `constructCollectionType`) instead of erasure.

## Notes

- `CacheConfig` also installs a `CacheErrorHandler` that logs and swallows
  Redis failures, so a Redis outage degrades to database reads instead of
  returning 500s.
- Cache TTL and key prefix are externalised
  (`spring.cache.redis.time-to-live`, `spring.cache.redis.key-prefix`).
