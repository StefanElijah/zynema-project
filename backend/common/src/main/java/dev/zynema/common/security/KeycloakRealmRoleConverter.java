package dev.zynema.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Maps Keycloak <em>realm roles</em> to Spring Security authorities.
 *
 * <p>Keycloak puts roles in {@code realm_access.roles}; Spring Security
 * expects authorities. A realm role {@code content-manager} becomes the
 * authority {@code ROLE_content-manager}, which is what
 * {@code hasRole("content-manager")} looks for (Spring prepends
 * {@code ROLE_} and we keep the name verbatim, no case mangling).
 *
 * <p>Client roles ({@code resource_access}) are intentionally ignored: this
 * platform authorizes on realm roles only, so a token issued for a different
 * client cannot smuggle in unexpected grants.
 */
public final class KeycloakRealmRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String REALM_ACCESS_CLAIM = "realm_access";
    static final String ROLES_CLAIM = "roles";
    static final String ROLE_PREFIX = "ROLE_";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, extractAuthorities(jwt));
    }

    /**
     * Extracts realm roles from the token. Never throws: a token without the
     * claim (or with an unexpected shape) yields no roles, which the
     * authorization rules then treat as "not allowed".
     */
    public Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get(REALM_ACCESS_CLAIM);
        if (!(realmAccess instanceof Map<?, ?> realmAccessMap)) {
            return List.of();
        }
        Object roles = realmAccessMap.get(ROLES_CLAIM);
        if (!(roles instanceof Collection<?> roleNames)) {
            return List.of();
        }
        return roleNames.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .filter(role -> !role.isBlank())
            .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role))
            .toList();
    }
}
