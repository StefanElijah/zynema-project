package dev.zynema.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

/**
 * Applies the security posture every Zynema service shares: stateless,
 * no browser-oriented login mechanisms, JWT resource server with Keycloak
 * realm roles, and JSON 401/403 bodies.
 *
 * <p>Services with their own rules call this <em>after</em> declaring them:
 *
 * <pre>{@code
 * http.authorizeHttpRequests(auth -> auth...);
 * support.apply(http);
 * return http.build();
 * }</pre>
 *
 * Services with no specific rules get a default chain from
 * {@code CommonSecurityAutoConfiguration} instead.
 */
@RequiredArgsConstructor
public class ServletSecuritySupport {

    private final KeycloakRealmRoleConverter realmRoleConverter;
    private final ApiAuthenticationEntryPoint authenticationEntryPoint;
    private final ApiAccessDeniedHandler accessDeniedHandler;

    public void apply(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(realmRoleConverter))
                .authenticationEntryPoint(authenticationEntryPoint))
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));
    }
}
