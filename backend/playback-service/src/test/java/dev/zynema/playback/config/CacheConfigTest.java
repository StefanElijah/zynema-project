package dev.zynema.playback.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The cache wiring: the user-account region is typed and registered, unknown
 * names are refused, and a Redis failure degrades instead of propagating.
 */
class CacheConfigTest {

    private final CacheConfig config = new CacheConfig();

    @Test
    void registersTheKnownRegionAndRefusesUnknownOnes() {
        RedisCacheManager manager = config.cacheManager(
            mock(RedisConnectionFactory.class), new ObjectMapper(), "zynema:playback:");
        manager.afterPropertiesSet();

        assertThat(manager.getCache(CacheConfig.USER_ACCOUNT)).isNotNull();
        assertThat(manager.getCache("unknown")).isNull();
    }

    @Test
    void swallowsRedisFailuresOnEveryOperation() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn(CacheConfig.USER_ACCOUNT);
        RuntimeException failure = new RuntimeException("redis down");
        CacheErrorHandler handler = config.errorHandler();

        assertThatCode(() -> {
            handler.handleCacheGetError(failure, cache, "key");
            handler.handleCachePutError(failure, cache, "key", "value");
            handler.handleCacheEvictError(failure, cache, "key");
            handler.handleCacheClearError(failure, cache);
        }).doesNotThrowAnyException();
    }
}
