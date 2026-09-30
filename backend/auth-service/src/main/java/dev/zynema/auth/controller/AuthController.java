package dev.zynema.auth.controller;

import dev.zynema.auth.dto.AuthClientConfigDto;
import dev.zynema.auth.dto.CurrentUserDto;
import dev.zynema.common.security.ZynemaSecurityProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Identity of the current session and public OIDC settings")
public class AuthController {

    private static final String CLIENT_ID = "zynema-web";
    private static final List<String> SCOPES = List.of("openid", "profile", "email");

    private final ZynemaSecurityProperties securityProperties;

    public AuthController(ZynemaSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @GetMapping("/me")
    @Operation(summary = "Identity of the caller",
               description = "Derived from the validated JWT: subject, email, realm roles and authorities.")
    public CurrentUserDto me(@AuthenticationPrincipal Jwt jwt) {
        return new CurrentUserDto(
            jwt.getSubject(),
            jwt.getClaimAsString("preferred_username"),
            jwt.getClaimAsString("email"),
            Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
            fullName(jwt),
            realmRoles(jwt),
            authorities(jwt),
            jwt.getIssuer() == null ? null : jwt.getIssuer().toString(),
            jwt.getAudience(),
            jwt.getExpiresAt()
        );
    }

    @GetMapping("/public/config")
    @Operation(summary = "Public OIDC settings for the SPA",
               description = "Issuer, client id and scopes. Contains no secrets.")
    public AuthClientConfigDto clientConfig() {
        return new AuthClientConfigDto(
            securityProperties.issuerUri(),
            realmName(),
            CLIENT_ID,
            securityProperties.audience(),
            SCOPES
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> realmRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof List<?> roles) {
            return roles.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    private List<String> authorities(Jwt jwt) {
        // Authorities as Spring Security sees them: ROLE_ prefixed realm roles.
        return jwt.getClaims().get("realm_access") instanceof Map<?, ?> map
            && map.get("roles") instanceof List<?> roles
            ? roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(role -> "ROLE_" + role)
                .toList()
            : List.of();
    }

    private String fullName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        String given = jwt.getClaimAsString("given_name");
        String family = jwt.getClaimAsString("family_name");
        if (given == null && family == null) {
            return null;
        }
        return ((given == null ? "" : given) + " " + (family == null ? "" : family)).trim();
    }

    private String realmName() {
        String issuer = securityProperties.issuerUri();
        int index = issuer.lastIndexOf("/realms/");
        return index >= 0 ? issuer.substring(index + "/realms/".length()) : issuer;
    }
}
