package dev.zynema.user.service;

import dev.zynema.common.exception.BusinessRuleException;
import dev.zynema.common.exception.ResourceNotFoundException;
import dev.zynema.user.domain.Profile;
import dev.zynema.user.domain.User;
import dev.zynema.user.domain.WatchHistoryEntry;
import dev.zynema.user.domain.WatchlistItem;
import dev.zynema.user.dto.ProfileCreateRequest;
import dev.zynema.user.dto.ProfileDto;
import dev.zynema.user.dto.UserCreateRequest;
import dev.zynema.user.dto.UserDto;
import dev.zynema.user.dto.WatchHistoryDto;
import dev.zynema.user.dto.WatchProgressRequest;
import dev.zynema.user.dto.WatchlistAddRequest;
import dev.zynema.user.dto.WatchlistItemDto;
import dev.zynema.user.mapper.ActivityMapper;
import dev.zynema.user.mapper.UserMapper;
import dev.zynema.user.repository.ProfileRepository;
import dev.zynema.user.repository.UserRepository;
import dev.zynema.user.repository.WatchHistoryRepository;
import dev.zynema.user.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserCommandService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final WatchlistRepository watchlistRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final UserMapper userMapper;
    private final ActivityMapper activityMapper;

    // ────────────────────────────── users ─────────────────────────────

    public UserDto createUser(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("Email '%s' is already registered".formatted(request.email()));
        }
        User user = new User();
        user.setEmail(request.email());
        user.setDisplayName(request.displayName());
        user.setAvatarUrl(request.avatarUrl());
        user.setPreferredLanguage(request.preferredLanguage() == null ? "es" : request.preferredLanguage());
        return userMapper.toDto(userRepository.save(user));
    }

    // ──────────────────────────── profiles ────────────────────────────

    public ProfileDto createProfile(UUID userId, ProfileCreateRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (profileRepository.existsByUserIdAndNameIgnoreCase(userId, request.name())) {
            throw new BusinessRuleException("Profile '%s' already exists for this account".formatted(request.name()));
        }
        Profile profile = new Profile();
        profile.setUser(user);
        profile.setName(request.name());
        profile.setAvatarKey(request.avatarKey());
        profile.setKids(Boolean.TRUE.equals(request.kids()));
        profile.setLanguage(request.language() == null ? user.getPreferredLanguage() : request.language());
        return userMapper.toDto(profileRepository.save(profile));
    }

    public void deleteProfile(UUID userId, UUID profileId) {
        Profile profile = profileRepository.findByIdAndUserId(profileId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Profile", profileId));
        profileRepository.delete(profile);
    }

    // ──────────────────────────── watchlist ───────────────────────────

    public WatchlistItemDto addToWatchlist(UUID userId, UUID profileId, WatchlistAddRequest request) {
        Profile profile = requireProfile(userId, profileId);
        return watchlistRepository.findByProfileIdAndContentId(profileId, request.contentId())
            .map(activityMapper::toDto)
            .orElseGet(() -> {
                WatchlistItem item = new WatchlistItem();
                item.setProfile(profile);
                item.setContentId(request.contentId());
                return activityMapper.toDto(watchlistRepository.save(item));
            });
    }

    public void removeFromWatchlist(UUID userId, UUID profileId, UUID contentId) {
        requireProfile(userId, profileId);
        if (!watchlistRepository.existsByProfileIdAndContentId(profileId, contentId)) {
            throw new ResourceNotFoundException("Watchlist entry for content", contentId);
        }
        watchlistRepository.deleteByProfileIdAndContentId(profileId, contentId);
    }

    // ──────────────────────────── progress ────────────────────────────

    /**
     * Idempotent progress upsert: one row per (profile, movie) or
     * (profile, episode). A client may retry freely.
     */
    public WatchHistoryDto recordProgress(UUID userId, UUID profileId, WatchProgressRequest request) {
        Profile profile = requireProfile(userId, profileId);

        WatchHistoryEntry entry = (request.episodeId() == null
            ? watchHistoryRepository.findByProfileIdAndContentIdAndEpisodeIdIsNull(profileId, request.contentId())
            : watchHistoryRepository.findByProfileIdAndContentIdAndEpisodeId(profileId, request.contentId(), request.episodeId()))
            .orElseGet(() -> {
                WatchHistoryEntry created = new WatchHistoryEntry();
                created.setProfile(profile);
                created.setContentId(request.contentId());
                created.setEpisodeId(request.episodeId());
                return created;
            });

        entry.setPositionSeconds(request.positionSeconds());
        if (request.durationSeconds() != null) {
            entry.setDurationSeconds(request.durationSeconds());
        }
        entry.setCompleted(Boolean.TRUE.equals(request.completed())
            || isFinished(request.positionSeconds(), entry.getDurationSeconds()));
        entry.setLastWatchedAt(Instant.now());

        return activityMapper.toDto(watchHistoryRepository.save(entry));
    }

    /** A title counts as watched once 95% of its duration has been played. */
    private boolean isFinished(int positionSeconds, Integer durationSeconds) {
        return durationSeconds != null && durationSeconds > 0
            && positionSeconds >= Math.ceil(durationSeconds * 0.95);
    }

    private Profile requireProfile(UUID userId, UUID profileId) {
        return profileRepository.findByIdAndUserId(profileId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Profile", profileId));
    }
}
