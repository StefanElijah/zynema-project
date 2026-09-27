package dev.zynema.auth.dto;

import java.time.Instant;
import java.util.List;

/**
 * What the platform knows about the caller, derived exclusively from the
 * validated JWT. The frontend uses it to render the session and to decide
 * which admin affordances to show.
 */
public record CurrentUserDto(
    String subject,
    String username,
    String email,
    boolean emailVerified,
    String fullName,
    List<String> roles,
    List<String> authorities,
    String issuer,
    List<String> audience,
    Instant expiresAt
) {
}
