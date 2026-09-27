package dev.zynema.payment.config;

import dev.zynema.common.security.ServletSecuritySupport;
import dev.zynema.common.security.ZynemaSecurityPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Billing authorization rules.
 *
 * <p>The pricing catalogue is public (a pricing page must not require an
 * account); everything that touches a subscription or a payment requires a
 * valid token and is scoped to the caller's account inside the service.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain paymentSecurityFilterChain(HttpSecurity http, ServletSecuritySupport support) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers(ZynemaSecurityPaths.PUBLIC_OPERATIONS).permitAll()
            .requestMatchers(ZynemaSecurityPaths.PUBLIC_API_DOCS).permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/payments/plans").permitAll()
            .anyRequest().authenticated());

        support.apply(http);
        return http.build();
    }
}
