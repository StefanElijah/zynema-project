package dev.zynema.user.config;

import dev.zynema.common.security.ServletSecuritySupport;
import dev.zynema.common.security.ZynemaSecurityPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * User-service authorization rules.
 *
 * <p>Self-service lives under {@code /api/v1/users/me/**}: the caller's
 * identity comes from the token, never from the URL, so an account cannot even
 * express a request against another account.
 *
 * <p>The id-addressed endpoints stay for back-office use and require the
 * {@code admin} role. {@code /me} is matched first on purpose — the rules are
 * evaluated in order and the broader pattern below would otherwise swallow it.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain userSecurityFilterChain(HttpSecurity http, ServletSecuritySupport support) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            // Operational endpoints and API docs stay public (see ZynemaSecurityPaths).
            .requestMatchers(ZynemaSecurityPaths.PUBLIC_OPERATIONS).permitAll()
            .requestMatchers(ZynemaSecurityPaths.PUBLIC_API_DOCS).permitAll()
            .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**").authenticated()
            .requestMatchers("/api/v1/users/**").hasRole("admin")
            .anyRequest().authenticated());

        support.apply(http);
        return http.build();
    }
}
