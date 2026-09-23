package dev.zynema.catalog.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import dev.zynema.catalog.dto.ContentDetailDto;
import dev.zynema.catalog.dto.ContentSummaryDto;
import dev.zynema.catalog.dto.EpisodeDto;
import dev.zynema.catalog.dto.GenreDto;
import dev.zynema.common.dto.PageResponse;
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

/**
 * Cache configuration.
 *
 * <p>Design choices:
 * <ul>
 *   <li><strong>Typed JSON, no default typing.</strong> Each cache declares the
 *       exact type it stores. Jackson's default typing (writing {@code @class}
 *       hints) is a well-known deserialisation risk, so it is not used here.
 *       Adding a new cache means registering it below — enforced by
 *       {@code disableCreateOnMissingCache()}.</li>
 *   <li><strong>Graceful degradation.</strong> {@link #errorHandler()} logs and
 *       swallows Redis failures: if the cache is down the API keeps serving
 *       from the database instead of returning 500s.</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String CONTENT_DETAIL = "content-detail";
    public static final String CONTENT_LIST = "content-list";
    public static final String GENRES = "genres";
    public static final String SEASON_EPISODES = "season-episodes";

    @Bean
    public RedisCacheManager cacheManager(
        RedisConnectionFactory connectionFactory,
        ObjectMapper objectMapper,
        @Value("${spring.cache.redis.time-to-live:10m}") Duration timeToLive,
        @Value("${spring.cache.redis.key-prefix:zynema:catalog:}") String keyPrefix
    ) {
        // The application ObjectMapper already has JavaTimeModule registered by
        // Spring Boot; only the timestamp representation is adjusted.
        ObjectMapper cacheMapper = objectMapper.copy()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        TypeFactory types = cacheMapper.getTypeFactory();

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ZERO)
                .prefixCacheNameWith(keyPrefix))
            .withCacheConfiguration(CONTENT_DETAIL, jsonCache(cacheMapper, types.constructType(ContentDetailDto.class), timeToLive, keyPrefix))
            .withCacheConfiguration(CONTENT_LIST, jsonCache(cacheMapper, types.constructParametricType(PageResponse.class, ContentSummaryDto.class), timeToLive, keyPrefix))
            .withCacheConfiguration(GENRES, jsonCache(cacheMapper, types.constructCollectionType(List.class, GenreDto.class), timeToLive, keyPrefix))
            .withCacheConfiguration(SEASON_EPISODES, jsonCache(cacheMapper, types.constructCollectionType(List.class, EpisodeDto.class), timeToLive, keyPrefix))
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
