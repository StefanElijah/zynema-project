package dev.zynema.playback.service;

import dev.zynema.playback.domain.SessionStatus;
import dev.zynema.playback.dto.SessionDto;
import dev.zynema.playback.mapper.SessionMapper;
import dev.zynema.playback.repository.PlaybackSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read side of playback: the caller's own sessions, across devices. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaybackQueryService {

    private final PlaybackSessionRepository sessionRepository;
    private final PlaybackDependencies dependencies;
    private final SessionMapper mapper;

    public List<SessionDto> activeSessions(Jwt jwt) {
        return mapper.toDtoList(sessionRepository.findByUserIdAndStatusNotOrderByLastHeartbeatAtDesc(
            dependencies.resolveUserId(jwt), SessionStatus.ENDED));
    }
}
