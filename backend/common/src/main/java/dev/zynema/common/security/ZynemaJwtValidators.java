package dev.zynema.common.security;

import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.util.Collection;

/**
 * Token validation shared by the servlet and reactive decoders.
 *
 * <p>Default Spring validation checks timestamps and (when configured) the
 * issuer. We add the audience check: without it, any token minted by the same
 * realm for <em>another</em> client would be accepted by every service in the
 * platform.
 */
final class ZynemaJwtValidators {

    private ZynemaJwtValidators() {
    }

    static OAuth2TokenValidator<Jwt> forProperties(ZynemaSecurityProperties properties) {
        OAuth2TokenValidator<Jwt> timestampsAndIssuer =
            JwtValidators.createDefaultWithIssuer(properties.issuerUri());
        return new DelegatingOAuth2TokenValidator<>(timestampsAndIssuer, audience(properties.audience()));
    }

    private static OAuth2TokenValidator<Jwt> audience(String expectedAudience) {
        return new JwtClaimValidator<>(JwtClaimNames.AUD, claim -> switch (claim) {
            case String single -> single.equals(expectedAudience);
            case Collection<?> many -> many.contains(expectedAudience);
            case null, default -> false;
        });
    }
}
