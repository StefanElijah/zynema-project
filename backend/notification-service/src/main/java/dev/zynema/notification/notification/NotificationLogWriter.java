package dev.zynema.notification.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The delivery history. Written inside the handler's transaction, so a
 * rolled-back send leaves no entry (ADR-0026); the idempotent insert means the
 * dead-letter path can record a failure without racing the normal path.
 */
@Component
@RequiredArgsConstructor
public class NotificationLogWriter {

    private static final String INSERT = """
        INSERT INTO notification_log
            (notification_id, user_id, template, recipient, status, reason, source_event_id)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (notification_id) DO NOTHING
        """;

    private final JdbcTemplate jdbc;

    public void sent(UUID notificationId, UUID userId, String template, String recipient, UUID sourceEventId) {
        insert(notificationId, userId, template, recipient, "SENT", null, sourceEventId);
    }

    public void failed(UUID notificationId, UUID userId, String template, String recipient,
                       String reason, UUID sourceEventId) {
        insert(notificationId, userId, template, recipient, "FAILED", reason, sourceEventId);
    }

    private void insert(UUID notificationId, UUID userId, String template, String recipient,
                        String status, String reason, UUID sourceEventId) {
        jdbc.update(INSERT, notificationId, userId, template, recipient, status, reason, sourceEventId);
    }
}
