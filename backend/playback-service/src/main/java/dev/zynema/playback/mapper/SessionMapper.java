package dev.zynema.playback.mapper;

import dev.zynema.playback.domain.PlaybackSession;
import dev.zynema.playback.dto.SessionDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SessionMapper {

    SessionDto toDto(PlaybackSession session);

    List<SessionDto> toDtoList(List<PlaybackSession> sessions);
}
