package dev.zynema.playback;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.zynema.common.exception.ConflictException;
import dev.zynema.common.messaging.EventEnvelopes;
import dev.zynema.common.messaging.OutboxRelay;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.PlaybackEvent;
import dev.zynema.playback.events.SessionEventStore;
import dev.zynema.playback.events.SessionEventStore.SessionStream;
import dev.zynema.playback.events.SessionState;
import dev.zynema.playback.repository.PlaybackSessionRepository;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The event sourcing contract of playback-service (ADR-0009, ADR-0027):
 * the log is append-only and is the source of truth, the projection is the
 * fold of the log, and the same events leave through the outbox with the same
 * identity.
 */
@AutoConfigureMockMvc
class EventSourcingTests extends AbstractPlaybackIntegrationTest {

    private static final String DEMO_SUBJECT = "11111111-1111-4111-8111-111111111111";
    private static final String DEMO_USER = "71000000-0000-4000-8000-000000000001";
    private static final String DEMO_PROFILE = "72000000-0000-4000-8000-000000000001";
    private static final UUID ARCADE_CONTENT = UUID.fromString("a2000000-0000-4000-8000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlaybackSessionRepository sessionRepository;

    @Autowired
    private SessionEventStore eventStore;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private TransactionTemplate transactions;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @BeforeEach
    void reset() {
        sessionRepository.deleteAll();
        resetEventStore();
        DEPENDENCIES.resetAll();
        stubDependencies();
    }

    @Test
    @DisplayName("the stream is the session: sequences are dense, ordered and append-only")
    void theStreamIsTheSession() throws Exception {
        UUID sessionId = start();

        heartbeat(sessionId, 900);
        end(sessionId, 1200);

        List<Map<String, Object>> log = jdbc.queryForList("""
            SELECT sequence, type FROM session_events
            WHERE session_id = ? ORDER BY sequence
            """, sessionId);

        assertThat(log).extracting(row -> ((Number) row.get("sequence")).longValue())
            .containsExactly(1L, 2L, 3L);
        assertThat(log).extracting(row -> row.get("type"))
            .containsExactly("playback.session-started", "playback.session-progressed",
                "playback.session-stopped");

        // The fold rebuilds exactly what the projection says: watched seconds
        // accumulate the forward deltas, and the stop keeps the final position.
        SessionState replayed = eventStore.load(sessionId).state();
        Map<String, Object> projected = jdbc.queryForMap(
            "SELECT status, position_seconds FROM playback_sessions WHERE id = ?", sessionId);

        assertThat(replayed.status().name()).isEqualTo(projected.get("status"));
        assertThat(replayed.positionSeconds())
            .isEqualTo(((Number) projected.get("position_seconds")).intValue());
        assertThat(replayed.watchedSeconds()).isEqualTo(1200);
        assertThat(replayed.endedAt()).isNotNull();
    }

    @Test
    @DisplayName("the event leaves through the outbox: same id as the log row, session id as the key")
    void eventsLeaveThroughTheOutboxWithTheirOwnIdentity() throws Exception {
        UUID sessionId = start();

        relay.publishPending();

        ConsumerRecord<String, PlaybackEvent> record = drain(sessionId, 1).get(0);
        assertThat(record.key()).isEqualTo(sessionId.toString());
        assertThat(record.value()).isInstanceOf(PlaybackEvent.SessionStarted.class);

        PlaybackEvent.SessionStarted started = (PlaybackEvent.SessionStarted) record.value();
        assertThat(started.contentTitle()).isEqualTo("Arcane");
        assertThat(started.profileId()).isEqualTo(UUID.fromString(DEMO_PROFILE));
        assertThat(EventEnvelopes.of(record).type()).isEqualTo("playback.session-started");

        UUID logEventId = jdbc.queryForObject(
            "SELECT event_id FROM session_events WHERE session_id = ?", UUID.class, sessionId);
        assertThat(EventEnvelopes.of(record).eventId()).isEqualTo(logEventId);
    }

    @Test
    @DisplayName("snapshots keep the fold short: a load survives without the events they cover")
    void snapshotsKeepTheFoldShort() throws Exception {
        UUID sessionId = start();          // sequence 1
        heartbeat(sessionId, 100);         // sequence 2 → snapshot (every 2 in tests)

        Long snapshotSequence = jdbc.queryForObject(
            "SELECT sequence FROM session_snapshots WHERE session_id = ?", Long.class, sessionId);
        assertThat(snapshotSequence).isEqualTo(2L);

        // What a compaction job would do with covered events: the snapshot is
        // now the only place where that state lives.
        jdbc.update("DELETE FROM session_events WHERE session_id = ? AND sequence <= ?",
            sessionId, snapshotSequence);

        heartbeat(sessionId, 250);         // sequence 3, loaded from the snapshot

        SessionState state = eventStore.load(sessionId).state();
        assertThat(state.positionSeconds()).isEqualTo(250);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM session_events WHERE session_id = ?", Long.class, sessionId))
            .isEqualTo(1L);
    }

    @Test
    @DisplayName("a rollback takes the event and its outbox row with it")
    void rolledBackAppendLeavesNoTrace() {
        UUID sessionId = UUID.randomUUID();

        transactions.execute(status -> {
            eventStore.append(SessionStream.empty(), new PlaybackEvent.SessionStarted(sessionId,
                UUID.fromString(DEMO_USER), UUID.fromString(DEMO_PROFILE), ARCADE_CONTENT, null,
                "Arcane", "test", 0, 2400, Instant.now()));
            status.setRollbackOnly();
            return null;
        });

        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM session_events WHERE session_id = ?", Long.class, sessionId)).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM outbox WHERE subject = ?", Long.class, sessionId.toString())).isZero();
    }

    @Test
    @DisplayName("two writers from the same sequence collide: the loser gets a 409, not an interleaved stream")
    void staleStreamIsAConflict() throws Exception {
        UUID sessionId = start();
        SessionStream stale = eventStore.load(sessionId);

        eventStore.append(stale, progressed(sessionId, 100));

        assertThatThrownBy(() -> eventStore.append(stale, progressed(sessionId, 200)))
            .isInstanceOf(ConflictException.class);

        assertThat(eventStore.load(sessionId).state().positionSeconds()).isEqualTo(100);
    }

    // ───────────────────────────── helpers ────────────────────────────

    private PlaybackEvent.SessionProgressed progressed(UUID sessionId, int position) {
        return new PlaybackEvent.SessionProgressed(sessionId, UUID.fromString(DEMO_USER),
            ARCADE_CONTENT, position, 2400, Instant.now());
    }

    private UUID start() throws Exception {
        String body = mockMvc.perform(post("/api/v1/playback/sessions").with(asDemo())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"profileId": "%s", "contentId": "%s", "device": "test"}
                    """.formatted(DEMO_PROFILE, ARCADE_CONTENT)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id"));
    }

    private void heartbeat(UUID sessionId, int position) throws Exception {
        mockMvc.perform(put("/api/v1/playback/sessions/" + sessionId + "/position").with(asDemo())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"positionSeconds\": " + position + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.positionSeconds", is(position)));
    }

    private void end(UUID sessionId, int position) throws Exception {
        mockMvc.perform(post("/api/v1/playback/sessions/" + sessionId + "/end").with(asDemo())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"positionSeconds\": " + position + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("ENDED")));
    }

    private List<ConsumerRecord<String, PlaybackEvent>> drain(UUID key, int expected) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "event-sourcing-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonSchemaDeserializer.class);
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put("json.value.type", PlaybackEvent.class.getName());

        List<ConsumerRecord<String, PlaybackEvent>> records = new ArrayList<>();
        try (KafkaConsumer<String, PlaybackEvent> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(KafkaTopics.PLAYBACK_EVENTS));
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
            while (records.size() < expected && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (key.toString().equals(record.key())) {
                        records.add(record);
                    }
                });
            }
        }
        return records;
    }

    private static RequestPostProcessor asDemo() {
        return jwt().jwt(jwt -> jwt.subject(DEMO_SUBJECT).claim("email", "demo@zynema.dev"))
            .authorities(new SimpleGrantedAuthority("ROLE_user"));
    }

    private void stubDependencies() {
        DEPENDENCIES.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/users/me"))
            .willReturn(WireMock.aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"id": "%s", "email": "demo@zynema.dev", "displayName": "Demo User"}
                    """.formatted(DEMO_USER))));
        DEPENDENCIES.stubFor(WireMock.put(WireMock.urlMatching("/api/v1/users/me/profiles/.*/progress"))
            .willReturn(WireMock.aResponse().withStatus(200)));
        DEPENDENCIES.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/catalog/contents/" + ARCADE_CONTENT))
            .willReturn(WireMock.aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"id": "%s", "title": "Arcane", "type": "SERIES", "runtimeMinutes": 40}
                    """.formatted(ARCADE_CONTENT))));
        DEPENDENCIES.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/payments/subscriptions/me/entitlements"))
            .willReturn(WireMock.aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"active": true, "maxStreams": 1, "maxQuality": "FHD", "planCode": "standard", "validUntil": null}
                    """)));
    }
}
