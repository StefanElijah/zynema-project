package dev.zynema.playback.events;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

/**
 * Writes the read model of the session: {@code playback_sessions} as a
 * projection of the event log (ADR-0027).
 *
 * <p>The log is the source of truth; this table exists because the interesting
 * queries — open sessions of an account, concurrency count, ownership of a
 * stream — go across sessions, and folding every stream per request would be
 * the wrong shape. It is written <strong>in the same transaction as the
 * event</strong>, so a rollback cannot leave a projected session behind, and
 * it is rebuildable by replaying the log if it is ever lost.
 *
 * <p>JDBC and not JPA on purpose: the projection is derived data, and one
 * statement per event is the whole operation. Nothing here should develop a
 * lifecycle of its own.
 */
@Service
@RequiredArgsConstructor
public class SessionProjection {

    private static final String INSERT = """
        INSERT INTO playback_sessions
            (id, user_id, profile_id, content_id, episode_id, status, content_title,
             position_seconds, duration_seconds, device, started_at, last_heartbeat_at, ended_at)
        VALUES
            (:id, :userId, :profileId, :contentId, :episodeId, :status, :contentTitle,
             :positionSeconds, :durationSeconds, :device, :startedAt, :lastHeartbeatAt, :endedAt)
        ON CONFLICT DO NOTHING
        """;

    private static final String UPDATE = """
        UPDATE playback_sessions SET
            status = :status,
            content_title = :contentTitle,
            position_seconds = :positionSeconds,
            duration_seconds = :durationSeconds,
            device = :device,
            last_heartbeat_at = :lastHeartbeatAt,
            ended_at = :endedAt
        WHERE id = :id
        """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Inserts the first projection of a new session.
     *
     * <p>{@code ON CONFLICT DO NOTHING} instead of a constraint exception: the
     * partial unique index that allows one open session per profile and title
     * is the race's referee, and a losing start gets {@code false} with its
     * transaction still healthy — the event and outbox rows it appended can be
     * rolled back cleanly, and the caller can look up the winner.
     *
     * @return whether this start owns the open session
     */
    public boolean create(SessionState state) {
        return jdbc.update(INSERT, parameters(state)) == 1;
    }

    public void update(SessionState state) {
        if (jdbc.update(UPDATE, parameters(state)) == 0) {
            throw new IllegalStateException(
                "Session " + state.sessionId() + " has an event stream but no projected row");
        }
    }

    private MapSqlParameterSource parameters(SessionState state) {
        return new MapSqlParameterSource()
            .addValue("id", state.sessionId())
            .addValue("userId", state.userId())
            .addValue("profileId", state.profileId())
            .addValue("contentId", state.contentId())
            .addValue("episodeId", state.episodeId())
            .addValue("status", state.status().name())
            .addValue("contentTitle", state.contentTitle())
            .addValue("positionSeconds", state.positionSeconds())
            .addValue("durationSeconds", state.durationSeconds())
            .addValue("device", state.device())
            .addValue("startedAt", Timestamp.from(state.startedAt()))
            .addValue("lastHeartbeatAt", Timestamp.from(state.lastHeartbeatAt()))
            .addValue("endedAt", state.endedAt() == null ? null : Timestamp.from(state.endedAt()));
    }
}
