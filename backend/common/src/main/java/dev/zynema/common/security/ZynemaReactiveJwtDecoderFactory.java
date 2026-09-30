package dev.zynema.common.security;

import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Reactive counterpart of {@link ZynemaJwtDecoderFactory}. Keys are fetched
 * lazily on the first token, so the gateway starts even with Keycloak down.
 */
public final class ZynemaReactiveJwtDecoderFactory {

    private ZynemaReactiveJwtDecoderFactory() {
    }

    public static ReactiveJwtDecoder create(ZynemaSecurityProperties properties) {
        NimbusReactiveJwtDecoder decoder =
            NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        decoder.setJwtValidator(ZynemaJwtValidators.forProperties(properties));
        return decoder;
    }
}
