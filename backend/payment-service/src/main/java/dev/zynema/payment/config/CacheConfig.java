package dev.zynema.payment.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import dev.zynema.payment.dto.PlanDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Payment caching, following the same rules as catalog (ADR-0013): typed JSON
 * per cache, no Jackson default typing, fail-fast for unregistered caches, and
 * an error handler that degrades to the database instead of failing requests.
 *
 * <p>Two regions only:
 * <ul>
 *   <li>{@code plans} — static pricing data, read on every pricing page hit.</li>
 *   <li>{@code user-account} — subject to local user id, so an authenticated
 *       request does not pay for a Feign round trip when it can be avoided.</li>
 * </ul>
 * Entitlements are deliberately not cached: they must reflect a new
 * subscription immediately.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String PLANS = "plans";
    public static final String USER_ACCOUNT = "user-account";

    @Bean
    public RedisCacheManager cacheManager(
        RedisConnectionFactory connectionFactory,
        ObjectMapper objectMapper,
        @Value("${spring.cache.redis.time-to-live:10m}") Duration timeToLive,
        @Value("${spring.cache.redis.key-prefix:zynema:payment:}") String keyPrefix
    ) {
        ObjectMapper cacheMapper = objectMapper.copy()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        TypeFactory types = cacheMapper.getTypeFactory();

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ZERO)
                .prefixCacheNameWith(keyPrefix))
            .withCacheConfiguration(PLANS, jsonCache(cacheMapper,
                types.constructCollectionType(List.class, PlanDto.class), timeToLive, keyPrefix))
            .withCacheConfiguration(USER_ACCOUNT, jsonCache(cacheMapper,
                types.constructType(UUID.class), Duration.ofMinutes(5), keyPrefix))
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

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache GET failed on '{}' key='{}' — falling back to the database: {}",
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
