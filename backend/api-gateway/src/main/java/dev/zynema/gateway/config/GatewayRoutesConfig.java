package dev.zynema.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Programmatic route definitions.
 * Service IDs are discovered via Eureka, so we don't hardcode URLs.
 * Auth path: /api/auth/**  → AUTH-SERVICE
 * BFF path: /api/web/**  → BFF-SERVICE
 * Direct path: /api/catalog/**, /api/users/**, etc. → domain services
 */
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
            // ─── BFF routes (preferred for the frontend) ───
            .route("bff-web", r -> r
                .path("/api/web/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .circuitBreaker(cb -> cb.setName("bff-cb").setFallbackUri("forward:/fallback/bff")))
                .uri("lb://bff-service"))

            // ─── Auth (public endpoints only; protected ones go through BFF) ───
            .route("auth-public", r -> r
                .path("/api/auth/public/**")
                .filters(f -> f.stripPrefix(2))
                .uri("lb://auth-service"))

            // ─── Direct domain service access (for service-to-service or admin) ───
            .route("auth", r -> r
                .path("/api/auth/**")
                .filters(f -> f.stripPrefix(2).circuitBreaker(cb -> cb.setName("auth-cb")))
                .uri("lb://auth-service"))

            .route("catalog", r -> r
                .path("/api/catalog/**")
                .filters(f -> f.stripPrefix(2).circuitBreaker(cb -> cb.setName("catalog-cb")))
                .uri("lb://catalog-service"))

            .route("user", r -> r
                .path("/api/users/**")
                .filters(f -> f.stripPrefix(2).circuitBreaker(cb -> cb.setName("user-cb")))
                .uri("lb://user-service"))

            .route("payment", r -> r
                .path("/api/payments/**")
                .filters(f -> f.stripPrefix(2).circuitBreaker(cb -> cb.setName("payment-cb")))
                .uri("lb://payment-service"))

            .route("playback", r -> r
                .path("/api/playback/**")
                .filters(f -> f.stripPrefix(2).circuitBreaker(cb -> cb.setName("playback-cb")))
                .uri("lb://playback-service"))

            .route("notification", r -> r
                .path("/api/notifications/**")
                .filters(f -> f.stripPrefix(2).circuitBreaker(cb -> cb.setName("notification-cb")))
                .uri("lb://notification-service"))

            // ─── OpenAPI / Swagger UI aggregations ───
            .route("swagger", r -> r
                .path("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                .uri("lb://api-gateway"))

            .build();
    }
}
