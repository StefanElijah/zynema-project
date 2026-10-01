package dev.zynema.common.messaging;

import dev.zynema.common.web.CorrelationIdFilter;
import dev.zynema.events.UserCommand;
import dev.zynema.events.UserEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRecorderTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final OutboxSerializer serializer = mock(OutboxSerializer.class);
    private final OutboxRecorder recorder = new OutboxRecorder(jdbc, serializer);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void appendsWithAFreshIdentityAndTheCallersCorrelation() {
        UserEvent.RoleGranted payload =
            new UserEvent.RoleGranted(UUID.randomUUID(), UUID.randomUUID(), "subscriber", Instant.now());
        when(serializer.write(payload)).thenReturn("{\"json\":true}");
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-42");

        UUID id = recorder.append("zynema.user.events", "user-1", payload);

        ArgumentCaptor<Timestamp> occurredAt = ArgumentCaptor.forClass(Timestamp.class);
        verify(jdbc).update(anyString(), eq(id), eq("zynema.user.events"), eq("user-1"),
            eq("user.role-granted"), eq("{\"json\":true}"), eq("corr-42"), occurredAt.capture());
        assertThat(id).isNotNull();
        assertThat(occurredAt.getValue()).isNotNull();
    }

    @Test
    void keepsTheIdentityOfAnEventThatAlreadyHasOne() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-01T10:00:00Z");
        UserCommand.GrantRole command =
            new UserCommand.GrantRole(UUID.randomUUID(), UUID.randomUUID(), "subscriber");
        when(serializer.write(command)).thenReturn("{}");

        UUID returned = recorder.append("zynema.user.commands", "user-1", eventId, occurredAt, command);

        assertThat(returned).isEqualTo(eventId);
        verify(jdbc).update(anyString(), eq(eventId), eq("zynema.user.commands"), eq("user-1"),
            eq("user.grant-role"), eq("{}"), isNull(), eq(Timestamp.from(occurredAt)));
    }

}
