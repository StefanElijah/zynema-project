package dev.zynema.common.security;

/**
 * Paths that are public in every service, kept in one place so the gateway and
 * the services cannot drift apart.
 *
 * <p>Operational endpoints are public because Prometheus and the container
 * healthcheck need them on the internal network. API documentation is public
 * in this project for developer convenience; a production profile should
 * either remove it or protect it (tracked in the Phase 10 hardening list).
 */
public final class ZynemaSecurityPaths {

    private ZynemaSecurityPaths() {
    }

    public static final String[] PUBLIC_OPERATIONS = {
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/info",
        "/actuator/prometheus"
    };

    public static final String[] PUBLIC_API_DOCS = {
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html"
    };
}
