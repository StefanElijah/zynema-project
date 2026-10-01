package dev.zynema.common.messaging;

import dev.zynema.events.UserEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final OutboxSerializer serializer = mock(OutboxSerializer.class);
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final OutboxRelay relay = new OutboxRelay(jdbc, serializer, publisher, "payment-service", 100);

    private final UUID id = UUID.randomUUID();
    private final Instant occurredAt = Instant.parse("2026-01-01T10:00:00Z");
    private final UserEvent.RoleGranted payload =
        new UserEvent.RoleGranted(UUID.randomUUID(), UUID.randomUUID(), "subscriber", Instant.now());

    @Test
    void mapsTheRowAndPublishesItWithItsOwnIdentity() {
        stubQueryWithOneRow();

        relay.publishPending();

        ArgumentCaptor<EventMetadata> metadata = ArgumentCaptor.forClass(EventMetadata.class);
        verify(publisher).publish(eq("zynema.user.events"), eq("user-1"), metadata.capture(), eq(payload));
        assertThat(metadata.getValue().eventId()).isEqualTo(id);
        assertThat(metadata.getValue().type()).isEqualTo("user.role-granted");
        assertThat(metadata.getValue().source()).isEqualTo("payment-service");
        assertThat(metadata.getValue().time()).isEqualTo(occurredAt);
        assertThat(metadata.getValue().version()).isEqualTo("1.0");
        assertThat(metadata.getValue().correlationId()).isEqualTo("corr-1");

        verify(jdbc).update(anyString(), eq(id));
    }

    @Test
    void doesNothingWhenThereIsNothingPending() {
        when(jdbc.query(anyString(), any(RowMapper.class), anyInt())).thenReturn(List.of());

        relay.publishPending();

        verify(publisher, never()).publish(anyString(), anyString(), any(), any());
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void stopsTheBatchAtTheFirstFailureSoOrderIsKept() {
        UUID second = UUID.randomUUID();
        OutboxRelay.OutboxMessage first = message(id);
        OutboxRelay.OutboxMessage next = message(second);
        when(jdbc.query(anyString(), any(RowMapper.class), anyInt())).thenReturn(List.of(first, next));
        when(serializer.read(anyString(), anyString())).thenReturn(payload);
        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker down"));
        when(publisher.publish(anyString(), anyString(), any(), any())).thenReturn(failed);

        relay.publishPending();

        verify(publisher).publish(anyString(), anyString(), any(), any());
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any(), any(), any());
    }

    private OutboxRelay.OutboxMessage message(UUID messageId) {
        return new OutboxRelay.OutboxMessage(messageId, "zynema.user.events", "user-1",
            "user.role-granted", "{}", "corr-1", occurredAt);
    }

    private void stubQueryWithOneRow() {
        when(jdbc.query(anyString(), any(RowMapper.class), anyInt())).thenAnswer(invocation -> {
            RowMapper<OutboxRelay.OutboxMessage> mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getObject("id", UUID.class)).thenReturn(id);
            when(rs.getString("topic")).thenReturn("zynema.user.events");
            when(rs.getString("subject")).thenReturn("user-1");
            when(rs.getString("type")).thenReturn("user.role-granted");
            when(rs.getString("payload")).thenReturn("{}");
            when(rs.getString("correlation_id")).thenReturn("corr-1");
            when(rs.getTimestamp("occurred_at")).thenReturn(Timestamp.from(occurredAt));
            return List.of(mapper.mapRow(rs, 0));
        });
        when(serializer.read("user.role-granted", "{}")).thenReturn(payload);
        when(publisher.publish(anyString(), anyString(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));
    }
}
