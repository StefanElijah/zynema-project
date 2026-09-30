package dev.zynema.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * Configures the rate limiter used by {@link dev.zynema.gateway.filter.ApiRateLimitFilter}.
 *
 * <p>A single {@link RedisRateLimiter} is created and given one configuration
 * per route: {@code isAllowed(routeId, key)} resolves the config for that route,
 * so per-route limits need no extra limiter instances.
 *
 * <p>Why not the stock {@code RequestRateLimiter} filter: it answers a denied
 * request with an empty 429, breaking the platform's error contract. The
 * algorithm (an atomic Lua token bucket in Redis, shared by every gateway
 * instance) is the same one used here.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class GatewayRateLimitConfig {

    @Bean
    public RedisRateLimiter routeRateLimiter(
        ReactiveStringRedisTemplate redisTemplate,
        ConfigurationService configurationService,
        RateLimitProperties properties
    ) {
        @SuppressWarnings("unchecked")
        Class<List<Long>> resultType = (Class<List<Long>>) (Class<?>) List.class;
        RedisScript<List<Long>> script = RedisScript.of(
            new ClassPathResource("META-INF/scripts/request_rate_limiter.lua"), resultType);

        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate, script, configurationService);
        limiter.setIncludeHeaders(true);

        properties.routes().forEach((routeId, limit) -> limiter.getConfig().put(routeId,
            new RedisRateLimiter.Config()
                .setReplenishRate(limit.replenishRate())
                .setBurstCapacity(limit.burstCapacity())
                .setRequestedTokens(limit.requestedTokensOrDefault())));

        return limiter;
    }
}
