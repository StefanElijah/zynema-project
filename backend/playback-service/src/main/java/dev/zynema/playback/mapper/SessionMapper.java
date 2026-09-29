package dev.zynema.playback.mapper;

import dev.zynema.playback.domain.PlaybackSession;
import dev.zynema.playback.dto.SessionDto;
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

    List<SessionDto> toDtoList(List<PlaybackSession> sessions);
}
