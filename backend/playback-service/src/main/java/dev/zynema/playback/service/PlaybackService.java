package dev.zynema.playback.service;

import dev.zynema.common.exception.BusinessRuleException;
import dev.zynema.common.exception.ConflictException;
import dev.zynema.common.exception.DownstreamServiceException;
import dev.zynema.common.exception.ResourceNotFoundException;
import dev.zynema.common.exception.SubscriptionRequiredException;
import dev.zynema.events.PlaybackEvent;
import dev.zynema.playback.client.CatalogServiceClient;
import dev.zynema.playback.client.PaymentServiceClient;
import dev.zynema.playback.domain.PlaybackSession;
import dev.zynema.playback.domain.SessionStatus;
import dev.zynema.playback.dto.PositionRequest;
import dev.zynema.playback.dto.SessionDto;
import dev.zynema.playback.dto.StartSessionRequest;
import dev.zynema.playback.events.SessionEventStore;
import dev.zynema.playback.events.SessionEventStore.SessionStream;
import dev.zynema.playback.events.SessionProjection;
import dev.zynema.playback.events.SessionState;
import dev.zynema.playback.mapper.SessionMapper;
import dev.zynema.playback.repository.PlaybackSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Session lifecycle, written as commands over an event-sourced aggregate
 * (ADR-0009, ADR-0027).
 *
 * <p>Every command loads the stream, appends one event and projects the new
 * state — all in one transaction. Starting a title that is already open on the
 * same profile resumes that session instead of stacking a new one (enforced by
 * a partial unique index as well, so a race cannot produce two open sessions).
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
    private final SessionEventStore eventStore;
    private final SessionProjection projection;

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

        PlaybackEvent.SessionStarted started = new PlaybackEvent.SessionStarted(UUID.randomUUID(), userId,
            request.profileId(), request.contentId(), request.episodeId(), content.title(), request.device(),
            0, content.runtimeMinutes() == null ? null : content.runtimeMinutes() * 60, Instant.now());

        SessionState state = eventStore.append(SessionStream.empty(), started).state();
        if (!projection.create(state)) {
            // Two starts raced: the partial index kept a single open session,
            // and this transaction took its event and outbox rows with it.
            log.info("Concurrent start for profile {} and content {}: returning the open session",
                request.profileId(), request.contentId());
            return mapper.toDto(findOpenSession(request).orElseThrow(() -> new ConflictException(
                "This title is being started concurrently on the same profile; retry the request")));
        }
        return mapper.toDto(state);
    }

    @Transactional
    public SessionDto heartbeat(UUID sessionId, PositionRequest request, Jwt jwt) {
        UUID userId = dependencies.resolveUserId(jwt);
        SessionStream stream = eventStore.load(sessionId);
        SessionState state = requireOpen(stream.state(), userId);

        PlaybackEvent.SessionProgressed progressed = new PlaybackEvent.SessionProgressed(sessionId, userId,
            state.contentId(), request.positionSeconds(), state.durationSeconds(), Instant.now());
        SessionState updated = eventStore.append(stream, progressed).state();
        projection.update(updated);

        dependencies.relayProgress(updated.profileId(), updated.contentId(), updated.episodeId(),
            updated.positionSeconds(), updated.durationSeconds());
        return mapper.toDto(updated);
    }

    @Transactional
    public SessionDto end(UUID sessionId, PositionRequest request, Jwt jwt) {
        UUID userId = dependencies.resolveUserId(jwt);
        SessionStream stream = eventStore.load(sessionId);
        SessionState state = requireOpen(stream.state(), userId);

        PlaybackEvent.SessionStopped stopped = new PlaybackEvent.SessionStopped(sessionId, userId,
            state.contentId(), request.positionSeconds(), state.watchedAt(request.positionSeconds()),
            Instant.now());
        SessionState updated = eventStore.append(stream, stopped).state();
        projection.update(updated);

        dependencies.relayProgress(updated.profileId(), updated.contentId(), updated.episodeId(),
            updated.positionSeconds(), updated.durationSeconds());
        return mapper.toDto(updated);
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
        SessionStatus status = SessionStatus.ENDED;
        return request.episodeId() == null
            ? sessionRepository.findByProfileIdAndContentIdAndEpisodeIdIsNullAndStatusNot(
                request.profileId(), request.contentId(), status)
            : sessionRepository.findByProfileIdAndContentIdAndEpisodeIdAndStatusNot(
                request.profileId(), request.contentId(), request.episodeId(), status);
    }

    /**
     * The owner of the stream is the only one who can move it, and a closed
     * stream accepts no commands: heartbeats after the player ended are the
     * client catching up, not new facts (ADR-0027).
     */
    private SessionState requireOpen(SessionState state, UUID userId) {
        if (!state.userId().equals(userId)) {
            throw new ResourceNotFoundException("Playback session", state.sessionId());
        }
        if (!state.isOpen()) {
            throw new ConflictException("The session has already ended");
        }
        return state;
    }
}
