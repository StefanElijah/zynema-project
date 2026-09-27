package dev.zynema.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZynemaSecurityPropertiesTest {

    @Test
    @DisplayName("blank values fall back to the local development defaults")
    void blankValuesUseDefaults() {
        ZynemaSecurityProperties properties = new ZynemaSecurityProperties(null, "  ", null);

        assertThat(properties.issuerUri()).isEqualTo(ZynemaSecurityProperties.DEFAULT_ISSUER);
        assertThat(properties.jwkSetUri())
            .isEqualTo(ZynemaSecurityProperties.DEFAULT_ISSUER + "/protocol/openid-connect/certs");
        assertThat(properties.audience()).isEqualTo(ZynemaSecurityProperties.DEFAULT_AUDIENCE);
    }

    @Test
    @DisplayName("an explicit JWKS URI is respected (split-horizon deployment)")
    void explicitJwkSetUriWins() {
        ZynemaSecurityProperties properties = new ZynemaSecurityProperties(
            "http://localhost:8180/realms/zynema",
            "http://keycloak:8080/realms/zynema/protocol/openid-connect/certs",
            "zynema-api");

        assertThat(properties.issuerUri()).isEqualTo("http://localhost:8180/realms/zynema");
        assertThat(properties.jwkSetUri()).startsWith("http://keycloak:8080");
        assertThat(properties.audience()).isEqualTo("zynema-api");
    }

    @Test
    @DisplayName("the JWKS URI is derived from the issuer when only the issuer is set")
    void jwkSetUriDerivesFromIssuer() {
        ZynemaSecurityProperties properties = new ZynemaSecurityProperties(
            "https://auth.example.com/realms/zynema", null, null);

        assertThat(properties.jwkSetUri())
            .isEqualTo("https://auth.example.com/realms/zynema/protocol/openid-connect/certs");
    }
}
