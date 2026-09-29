package dev.zynema.notification.contact;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * The contact projection: user id to email, fed only by
 * {@code UserEvent.UserRegistered} (ADR-0025).
 *
 * <p>Commands and payment events name a user, never an address; the one event
 * that carries the email is the registration. Keeping a local projection means
 * a notification does not need a synchronous call back into user-service, and
 * the email never travels around the platform.
 */
@Component
@RequiredArgsConstructor
public class ContactDirectory {

    private static final String UPSERT = """
        INSERT INTO contacts (user_id, email, display_name)
        VALUES (?, ?, ?)
        ON CONFLICT (user_id) DO UPDATE
            SET email = EXCLUDED.email, display_name = EXCLUDED.display_name, updated_at = now()
        """;

    private final JdbcTemplate jdbc;

    public void upsert(UUID userId, String email, String displayName) {
        jdbc.update(UPSERT, userId, email, displayName);
    }

    public Optional<Contact> find(UUID userId) {
        return jdbc.query("SELECT user_id, email, display_name FROM contacts WHERE user_id = ?",
            (rs, rowNum) -> new Contact(
                rs.getObject("user_id", UUID.class),
                rs.getString("email"),
                rs.getString("display_name")),
            userId).stream().findFirst();
    }

    public record Contact(UUID userId, String email, String displayName) {
    }
}
