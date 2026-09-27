package dev.zynema.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

/**
 * Reactive counterpart of {@link ServletSecuritySupport}.
 *
 * <p>The gateway uses it to enforce authorization at the edge; downstream
 * services still validate the token themselves, so a request that bypasses the
 * gateway is not trusted (defence in depth).
 */
@RequiredArgsConstructor
public class ReactiveSecuritySupport {

    private final KeycloakRealmRoleConverter realmRoleConverter;
    private final ApiServerAuthenticationEntryPoint authenticationEntryPoint;
    private final ApiServerAccessDeniedHandler accessDeniedHandler;

    public void apply(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(this::convert))
                .authenticationEntryPoint(authenticationEntryPoint))
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));
    }

    private Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        return Mono.just(realmRoleConverter.convert(jwt));
    }
}
