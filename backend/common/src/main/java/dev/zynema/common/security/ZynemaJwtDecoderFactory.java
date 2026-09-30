package dev.zynema.common.security;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Builds the servlet JWT decoder from the split-horizon properties: issuer for
 * claim validation, JWKS URI for keys.
 *
 * <p>{@link NimbusJwtDecoder#withJwkSetUri(String)} fetches keys lazily on the
 * first token, so constructing the decoder costs no network round trip.
 */
public final class ZynemaJwtDecoderFactory {

    private ZynemaJwtDecoderFactory() {
    }

    public static JwtDecoder create(ZynemaSecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        decoder.setJwtValidator(ZynemaJwtValidators.forProperties(properties));
        return decoder;
    }
}
