package dev.zynema.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security settings shared by every Zynema resource server.
 *
 * <p>The issuer and the JWKS location are deliberately separate. Keycloak runs
 * behind two faces in this project (ADR-0015):
 *
 * <ul>
 *   <li><strong>issuer-uri</strong> — the public URL the browser uses and the
 *       value that ends up in the {@code iss} claim. Used for claim
 *       validation.</li>
 *   <li><strong>jwk-set-uri</strong> — where the service fetches signing keys.
 *       In Docker this is the internal service name, which the resource server
 *       can reach but the browser cannot.</li>
 * </ul>
 *
 * Separating them is what allows a single issuer value to be validated
 * everywhere without either side needing to resolve the other's hostname.
 *
 * @param issuerUri expected {@code iss} claim
 * @param jwkSetUri where to fetch the signing keys
 * @param audience  required value inside the {@code aud} claim
 */
@ConfigurationProperties(prefix = "zynema.security")
public record ZynemaSecurityProperties(
    String issuerUri,
    String jwkSetUri,
    String audience
) {

    public static final String DEFAULT_ISSUER = "http://localhost:8180/realms/zynema";
    public static final String DEFAULT_AUDIENCE = "zynema-api";

    public ZynemaSecurityProperties {
        if (issuerUri == null || issuerUri.isBlank()) {
            issuerUri = DEFAULT_ISSUER;
        }
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            jwkSetUri = issuerUri + "/protocol/openid-connect/certs";
        }
        if (audience == null || audience.isBlank()) {
            audience = DEFAULT_AUDIENCE;
        }
    }
}
