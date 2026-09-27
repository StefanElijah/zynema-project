package dev.zynema.playback.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
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
import java.util.UUID;

/**
 * Playback caching, same rules as catalog and payment (ADR-0013): typed JSON
 * per cache, no default typing, unregistered caches fail fast, and Redis
 * failures degrade to the source of truth instead of failing requests.
 *
 * <p>One region: subject to local user id, so a heartbeat does not pay for a
 * Feign round trip on every position update.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String USER_ACCOUNT = "user-account";

    @Bean
    public RedisCacheManager cacheManager(
        RedisConnectionFactory connectionFactory,
        ObjectMapper objectMapper,
        @Value("${spring.cache.redis.key-prefix:zynema:playback:}") String keyPrefix
    ) {
        ObjectMapper cacheMapper = objectMapper.copy()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        TypeFactory types = cacheMapper.getTypeFactory();

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ZERO)
                .prefixCacheNameWith(keyPrefix))
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
                log.warn("Cache GET failed on '{}' key='{}' — falling back to the source of truth: {}",
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
