package dev.zynema.playback.service;

import dev.zynema.common.exception.BusinessRuleException;
import dev.zynema.common.exception.DownstreamServiceException;
import dev.zynema.common.exception.ResourceNotFoundException;
import dev.zynema.common.exception.SubscriptionRequiredException;
import dev.zynema.playback.client.CatalogServiceClient;
import dev.zynema.playback.client.PaymentServiceClient;
import dev.zynema.playback.domain.PlaybackSession;
import dev.zynema.playback.domain.SessionStatus;
import dev.zynema.playback.dto.PositionRequest;
import dev.zynema.playback.dto.SessionDto;
import dev.zynema.playback.dto.StartSessionRequest;
import dev.zynema.playback.mapper.SessionMapper;
import dev.zynema.playback.repository.PlaybackSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Session lifecycle.
 *
 * <p>Starting a title that is already open on the same profile resumes that
 * session instead of stacking a new one (enforced by a partial unique index as
 * well, so a race cannot produce two open sessions).
 *
 * <p>Concurrency is a plan limit: the account may have as many open sessions as
 * the plan allows, which is the classic streaming rule and the reason
 * entitlements are resolved on every start.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaybackService {

    private final PlaybackSessionRepository sessionRepository;
    private final PlaybackDependencies dependencies;
    private final SessionMapper mapper;

    @Transactional
    public SessionDto start(StartSessionRequest request, Jwt jwt) {
        UUID userId = dependencies.resolveUserId(jwt);
        CatalogServiceClient.ContentSummary content = dependencies.requireContent(request.contentId());
        PaymentServiceClient.Entitlements entitlements = requireActivePlan(dependencies.entitlements());

        Optional<PlaybackSession> existing = findOpenSession(request);
        if (existing.isPresent()) {
            PlaybackSession session = existing.get();
            dependencies.relayProgress(session.getProfileId(), session.getContentId(), session.getEpisodeId(),
                session.getPositionSeconds(), session.getDurationSeconds());
            return mapper.toDto(session);
        }

        enforceConcurrencyLimit(userId, entitlements.maxStreams());

        PlaybackSession session = new PlaybackSession();
        session.setUserId(userId);
        session.setProfileId(request.profileId());
        session.setContentId(request.contentId());
        session.setEpisodeId(request.episodeId());
        session.setContentTitle(content.title());
        session.setDurationSeconds(content.runtimeMinutes() == null ? null : content.runtimeMinutes() * 60);
        session.setDevice(request.device());
        session.setStatus(SessionStatus.STARTED);

        try {
            return mapper.toDto(sessionRepository.saveAndFlush(session));
        } catch (DataIntegrityViolationException ex) {
            // Two starts raced: the index kept a single open session.
            log.info("Concurrent start for profile {} and content {}: returning the open session",
                request.profileId(), request.contentId());
            return mapper.toDto(findOpenSession(request)
                .orElseThrow(() -> ex));
        }
    }

    @Transactional
    public SessionDto heartbeat(UUID sessionId, PositionRequest request, Jwt jwt) {
        UUID userId = dependencies.resolveUserId(jwt);
        PlaybackSession session = ownedSession(sessionId, userId);
        session.heartbeat(request.positionSeconds(), Instant.now());
        PlaybackSession saved = sessionRepository.save(session);
        dependencies.relayProgress(saved.getProfileId(), saved.getContentId(), saved.getEpisodeId(),
            saved.getPositionSeconds(), saved.getDurationSeconds());
        return mapper.toDto(saved);
    }

    @Transactional
    public SessionDto end(UUID sessionId, PositionRequest request, Jwt jwt) {
        UUID userId = dependencies.resolveUserId(jwt);
        PlaybackSession session = ownedSession(sessionId, userId);
        session.end(request.positionSeconds(), Instant.now());
        PlaybackSession saved = sessionRepository.save(session);
        dependencies.relayProgress(saved.getProfileId(), saved.getContentId(), saved.getEpisodeId(),
            saved.getPositionSeconds(), saved.getDurationSeconds());
        return mapper.toDto(saved);
    }

    // ───────────────────────────── helpers ────────────────────────────

    /**
     * Watching is a paid feature: browsing the catalogue is public, starting a
     * session is not.
     *
     * <p>The two refusals are deliberately different. A plan that does not
     * exist is a paywall ({@code 402 SUBSCRIPTION_REQUIRED}); a plan that
     * cannot be checked is an outage ({@code 503}), because guessing "free"
     * would let a lapsed account watch and guessing "no" would lock out a
     * paying one.
     */
    private PaymentServiceClient.Entitlements requireActivePlan(PlaybackDependencies.EntitlementsResult result) {
        if (result.degraded()) {
            throw new DownstreamServiceException("payment-service",
                "Could not verify the account's plan");
        }
        if (!result.entitlements().active()) {
            throw new SubscriptionRequiredException(
                "An active subscription is required to start playback");
        }
        return result.entitlements();
    }

    private void enforceConcurrencyLimit(UUID userId, int maxStreams) {
        long open = sessionRepository.countByUserIdAndStatusNot(userId, SessionStatus.ENDED);
        if (open >= maxStreams) {
            throw new BusinessRuleException(
                "The plan allows %d concurrent stream(s) and %d are already open".formatted(maxStreams, open));
        }
    }

    private Optional<PlaybackSession> findOpenSession(StartSessionRequest request) {
        return request.episodeId() == null
            ? sessionRepository.findByProfileIdAndContentIdAndEpisodeIdIsNullAndStatusNot(
                request.profileId(), request.contentId(), SessionStatus.ENDED)
            : sessionRepository.findByProfileIdAndContentIdAndEpisodeIdAndStatusNot(
                request.profileId(), request.contentId(), request.episodeId(), SessionStatus.ENDED);
    }

    private PlaybackSession ownedSession(UUID sessionId, UUID userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Playback session", sessionId));
    }
}
