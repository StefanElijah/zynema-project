package dev.zynema.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    @Test
    @DisplayName("maps realm roles to ROLE_ authorities keeping the original name")
    void mapsRealmRoles() {
        Jwt jwt = jwtWithRealmRoles(List.of("user", "content-manager"));

        Collection<GrantedAuthority> authorities = converter.extractAuthorities(jwt);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
            .containsExactlyInAnyOrder("ROLE_user", "ROLE_content-manager");
    }

    @Test
    @DisplayName("the authentication principal is the token subject")
    void principalIsSubject() {
        Jwt jwt = jwtWithRealmRoles(List.of("user"));

        assertThat(converter.convert(jwt).getName()).isEqualTo("11111111-1111-4111-8111-111111111111");
    }

    @Test
    @DisplayName("a token without realm_access yields no authorities instead of failing")
    void missingRealmAccessYieldsNoAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
            .subject("s").claim("email", "x@zynema.dev").build();

        assertThat(converter.extractAuthorities(jwt)).isEmpty();
    }

    @Test
    @DisplayName("a malformed realm_access claim yields no authorities")
    void malformedRealmAccessYieldsNoAuthorities() {
        Jwt rolesNotCollection = Jwt.withTokenValue("token").header("alg", "none")
            .subject("s").claim("realm_access", Map.of("roles", "user")).build();
        Jwt realmAccessNotMap = Jwt.withTokenValue("token").header("alg", "none")
            .subject("s").claim("realm_access", List.of("user")).build();
        Jwt blankAndNonString = jwtWithRealmRoles(List.of("  "));

        assertThat(converter.extractAuthorities(rolesNotCollection)).isEmpty();
        assertThat(converter.extractAuthorities(realmAccessNotMap)).isEmpty();
        assertThat(converter.extractAuthorities(blankAndNonString)).isEmpty();
    }

    @Test
    @DisplayName("client roles are ignored: only realm roles grant access")
    void clientRolesAreIgnored() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
            .subject("s")
            .claim("realm_access", Map.of("roles", List.of("user")))
            .claim("resource_access", Map.of("zynema-web", Map.of("roles", List.of("admin"))))
            .build();

        assertThat(converter.extractAuthorities(jwt)).extracting(GrantedAuthority::getAuthority)
            .containsExactly("ROLE_user");
    }

    private Jwt jwtWithRealmRoles(List<String> roles) {
        return Jwt.withTokenValue("token").header("alg", "none")
            .subject("11111111-1111-4111-8111-111111111111")
            .claim("realm_access", Map.of("roles", roles))
            .build();
    }
}
