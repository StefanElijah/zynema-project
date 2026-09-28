package dev.zynema.bff.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import dev.zynema.bff.client.CatalogClient;
import dev.zynema.bff.dto.AccountView;
import dev.zynema.bff.dto.HomeView;
import dev.zynema.bff.dto.ProfileHomeView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * The aggregated-screen cache.
 *
 * <p>Same rules as the domain services (ADR-0013): <strong>typed</strong> JSON
 * per cache — no default typing, no polymorphic hints — and a missing entry is
 * simply a miss. {@code disableCreateOnMissingCache()} makes an unregistered
 * cache name a startup-visible bug instead of an unbounded one.
 *
 * <p>Three BFF-specific choices:
 * <ul>
 *   <li><strong>The cache aspect runs first</strong> ({@code order = HIGHEST}).
 *       A hit must be served even when the circuit breaker for that dependency
 *       is open — the cache is precisely what keeps the landing page alive
 *       during an outage.</li>
 *   <li><strong>No eviction, only TTLs.</strong> Writes happen in the domain
 *       services and the BFF is not their only consumer; short TTLs plus a
 *       graceful error handler keep the failure modes bounded and simple.</li>
 *   <li><strong>The synchronous {@link RedisCacheManager} on a WebFlux
 *       service.</strong> Spring caches the value a reactive method emits, but
 *       the cache access itself is a short blocking round trip to Redis. We
 *       accept it: the payloads are small, the keys stable, Redis is local, and
 *       the alternative — hand-rolled reactive caching — would move caching out
 *       of the annotations every other service uses. If it ever shows up in
 *       event-loop latency, the fix is a bounded-elastic offload around the
 *       cache operations (see the Fase 5 runbook note).</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
public class BffCacheConfig implements CachingConfigurer {

    public static final String HOME = "bff-home";
    public static final String CONTENT = "bff-content";
    public static final String ACCOUNT = "bff-account";
    public static final String PROFILE_HOME = "bff-profile-home";

    @Bean
    public RedisCacheManager cacheManager(
        RedisConnectionFactory connectionFactory,
        ObjectMapper objectMapper,
        BffProperties properties
    ) {
        BffProperties.Cache cache = properties.cache();
        ObjectMapper cacheMapper = objectMapper.copy()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        TypeFactory types = cacheMapper.getTypeFactory();

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ZERO)
                .prefixCacheNameWith(cache.keyPrefix()))
            .withInitialCacheConfigurations(Map.of(
                HOME, jsonCache(cacheMapper, types.constructType(HomeView.class),
                    cache.homeTtl(), cache.keyPrefix()),
                // The raw catalogue payload: it does not depend on who asks,
                // so one entry serves every caller and every screen that needs
                // the same title (detail page included).
                CONTENT, jsonCache(cacheMapper, types.constructType(CatalogClient.ContentDetail.class),
                    cache.contentTtl(), cache.keyPrefix()),
                ACCOUNT, jsonCache(cacheMapper, types.constructType(AccountView.class),
                    cache.accountTtl(), cache.keyPrefix()),
                PROFILE_HOME, jsonCache(cacheMapper, types.constructType(ProfileHomeView.class),
                    cache.profileHomeTtl(), cache.keyPrefix())))
            .disableCreateOnMissingCache()
            .build();
    }

    private RedisCacheConfiguration jsonCache(ObjectMapper mapper, JavaType type, Duration ttl, String keyPrefix) {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(ttl)
            .prefixCacheNameWith(keyPrefix)
            .disableCachingNullValues()
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, type)));
    }

    /**
     * A cache outage must degrade to "call the dependency", never to a 500.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache GET failed on '{}' key='{}' — falling back to the dependency: {}",
                    cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed on '{}' key='{}': {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache EVICT failed on '{}' key='{}': {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache CLEAR failed on '{}': {}", cache.getName(), exception.getMessage());
            }
        };
    }
}
