package dev.zynema.catalog.config;

import dev.zynema.common.security.ServletSecuritySupport;
import dev.zynema.common.security.ZynemaSecurityPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Catalog authorization rules (ADR-0002).
 *
 * <p>Browsing the catalog is anonymous, exactly like a public marketing site.
 * Everything that mutates content requires the {@code content-manager} or
 * {@code admin} realm role.
 *
 * <p>The shared security posture (stateless, JWT resource server, Keycloak
 * realm roles, JSON 401/403) comes from {@link ServletSecuritySupport}, which
 * is the same configuration every other service uses.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain catalogSecurityFilterChain(HttpSecurity http, ServletSecuritySupport support) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            // Operational endpoints and API docs stay public (see ZynemaSecurityPaths).
            .requestMatchers(ZynemaSecurityPaths.PUBLIC_OPERATIONS).permitAll()
            .requestMatchers(ZynemaSecurityPaths.PUBLIC_API_DOCS).permitAll()
            // Admin write API first: it must not fall through to the public GET rule.
            .requestMatchers("/api/v1/catalog/admin/**").hasAnyRole("content-manager", "admin")
            // Public read API.
            .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**").permitAll()
            // Anything else (future endpoints) requires a valid token.
            .anyRequest().authenticated());

        support.apply(http);
        return http.build();
    }
}
