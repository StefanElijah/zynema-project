package dev.zynema.auth.config;

import dev.zynema.common.security.ServletSecuritySupport;
import dev.zynema.common.security.ZynemaSecurityPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * auth-service is a resource server: it validates the Keycloak JWT and exposes
 * identity information about the current session.
 *
 * <p>Only the public configuration endpoint is anonymous. Everything else,
 * including {@code /api/v1/auth/me}, requires a valid token.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain authSecurityFilterChain(HttpSecurity http, ServletSecuritySupport support) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            // Operational endpoints and API docs stay public (see ZynemaSecurityPaths).
            .requestMatchers(ZynemaSecurityPaths.PUBLIC_OPERATIONS).permitAll()
            .requestMatchers(ZynemaSecurityPaths.PUBLIC_API_DOCS).permitAll()
            .requestMatchers("/api/v1/auth/public/**").permitAll()
            .anyRequest().authenticated());

        support.apply(http);
        return http.build();
    }
}
