package dev.zynema.playback.config;

import dev.zynema.common.security.ServletSecuritySupport;
import dev.zynema.common.security.ZynemaSecurityPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Playback authorization rules.
 *
 * <p>Everything requires a token: there is no such thing as an anonymous
 * playback session. Sessions are scoped to the caller's account inside the
 * service, and the profile is validated by user-service when progress is
 * forwarded.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain playbackSecurityFilterChain(HttpSecurity http, ServletSecuritySupport support) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers(ZynemaSecurityPaths.PUBLIC_OPERATIONS).permitAll()
            .requestMatchers(ZynemaSecurityPaths.PUBLIC_API_DOCS).permitAll()
            .anyRequest().authenticated());

        support.apply(http);
        return http.build();
    }
}
