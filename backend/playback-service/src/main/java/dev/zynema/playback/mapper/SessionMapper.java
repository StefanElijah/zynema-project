package dev.zynema.playback.mapper;

import dev.zynema.playback.domain.PlaybackSession;
import dev.zynema.playback.dto.SessionDto;
import dev.zynema.playback.events.SessionState;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SessionMapper {

    /**
     * The stream path is a function of the session id, not a stored column:
     * every session exposes the same two manifest endpoints (ADR-0024).
     */
    @Mapping(target = "streamPath",
        expression = "java(\"/api/v1/playback/stream/\" + session.getId() + \"/master.m3u8\")")
    SessionDto toDto(PlaybackSession session);

    /**
     * The write path answers with the folded state, not with a re-read of the
     * projection: same JSON, one less query, and the response is the state the
     * event just produced.
     */
    @Mapping(target = "id", source = "sessionId")
    @Mapping(target = "streamPath",
        expression = "java(\"/api/v1/playback/stream/\" + state.sessionId() + \"/master.m3u8\")")
    SessionDto toDto(SessionState state);

    List<SessionDto> toDtoList(List<PlaybackSession> sessions);
}
