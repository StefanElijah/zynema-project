package dev.zynema.user.service;

import dev.zynema.common.exception.ResourceNotFoundException;
import dev.zynema.user.domain.Profile;
import dev.zynema.user.dto.ProfileDto;
import dev.zynema.user.dto.UserDto;
import dev.zynema.user.dto.WatchHistoryDto;
import dev.zynema.user.dto.WatchlistItemDto;
import dev.zynema.user.mapper.ActivityMapper;
import dev.zynema.user.mapper.UserMapper;
import dev.zynema.user.repository.ProfileRepository;
import dev.zynema.user.repository.UserRepository;
import dev.zynema.user.repository.WatchHistoryRepository;
import dev.zynema.user.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read side of user-service. Every profile-scoped lookup also checks that the
 * profile belongs to the user in the path, so an account can never read
 * another account's activity.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final WatchlistRepository watchlistRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final UserMapper userMapper;
    private final ActivityMapper activityMapper;

    public UserDto getUser(UUID userId) {
        return userMapper.toDto(userRepository.findWithProfilesById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId)));
    }

    public UserDto getUserByEmail(String email) {
        return userMapper.toDto(userRepository.findWithProfilesByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User", email)));
    }

    public List<ProfileDto> listProfiles(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        return userMapper.toProfileDtoList(profileRepository.findByUserIdOrderByNameAsc(userId));
    }

    public List<WatchlistItemDto> listWatchlist(UUID userId, UUID profileId) {
        requireProfile(userId, profileId);
        return activityMapper.toWatchlistDtoList(
            watchlistRepository.findByProfileIdOrderByAddedAtDesc(profileId));
    }

    public List<WatchHistoryDto> listHistory(UUID userId, UUID profileId, int limit) {
        requireProfile(userId, profileId);
        return activityMapper.toHistoryDtoList(watchHistoryRepository
            .findByProfileIdOrderByLastWatchedAtDesc(profileId, PageRequest.of(0, clamp(limit))));
    }

    public List<WatchHistoryDto> listContinueWatching(UUID userId, UUID profileId, int limit) {
        requireProfile(userId, profileId);
        return activityMapper.toHistoryDtoList(watchHistoryRepository
            .findInProgress(profileId, PageRequest.of(0, clamp(limit))));
    }

    private Profile requireProfile(UUID userId, UUID profileId) {
        return profileRepository.findByIdAndUserId(profileId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Profile", profileId));
    }

    private int clamp(int limit) {
        if (limit < 1) {
            return 20;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }
}
