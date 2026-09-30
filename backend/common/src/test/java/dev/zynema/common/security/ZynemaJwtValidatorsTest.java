package dev.zynema.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ZynemaJwtValidatorsTest {

    private static final String ISSUER = "http://localhost:8180/realms/zynema";

    private final OAuth2TokenValidator<Jwt> validator = ZynemaJwtValidators.forProperties(
        new ZynemaSecurityProperties(ISSUER, null, "zynema-api"));

    @Test
    @DisplayName("accepts a token with the right issuer and audience")
    void acceptsValidToken() {
        assertThat(validator.validate(token(ISSUER, List.of("zynema-api"))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("rejects a token issued by another realm")
    void rejectsWrongIssuer() {
        OAuth2TokenValidatorResult result = validator.validate(token("http://evil.example/realms/zynema", List.of("zynema-api")));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anySatisfy(error ->
            assertThat(error.getDescription()).contains("iss"));
    }

    @Test
    @DisplayName("rejects a token minted for a different audience")
    void rejectsWrongAudience() {
        OAuth2TokenValidatorResult result = validator.validate(token(ISSUER, List.of("another-client")));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anySatisfy(error ->
            assertThat(error.getErrorCode()).isEqualTo("invalid_token"));
    }

    @Test
    @DisplayName("rejects a token with no audience at all")
    void rejectsMissingAudience() {
        assertThat(validator.validate(token(ISSUER, null)).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("rejects an expired token (beyond the 60s default clock skew)")
    void rejectsExpiredToken() {
        Jwt expired = Jwt.withTokenValue("token").header("alg", "none")
            .subject("s").issuer(ISSUER).audience(List.of("zynema-api"))
            .issuedAt(Instant.now().minusSeconds(3600))
            .expiresAt(Instant.now().minusSeconds(600))
            .build();

        assertThat(validator.validate(expired).hasErrors()).isTrue();
    }

    private Jwt token(String issuer, List<String> audience) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none")
            .subject("11111111-1111-4111-8111-111111111111")
            .issuer(issuer)
            .issuedAt(Instant.now().minusSeconds(10))
            .expiresAt(Instant.now().plusSeconds(600));
        if (audience != null) {
            builder.audience(audience);
        }
        return builder.build();
    }
}
