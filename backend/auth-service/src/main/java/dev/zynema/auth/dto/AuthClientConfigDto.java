package dev.zynema.auth.dto;

import java.util.List;

/**
 * Public OIDC settings the frontend needs to start a PKCE flow. Everything
 * here is public by definition; secrets are never exposed.
 */
public record AuthClientConfigDto(
    String issuer,
    String realm,
    String clientId,
    String audience,
    List<String> scopes
) {
}
