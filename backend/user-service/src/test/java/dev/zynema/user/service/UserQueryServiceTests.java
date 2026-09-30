package dev.zynema.user.service;

import dev.zynema.common.exception.ResourceNotFoundException;
import dev.zynema.user.AbstractUserIntegrationTest;
import dev.zynema.user.domain.User;
import dev.zynema.user.dto.ProfileDto;
import dev.zynema.user.dto.UserDto;
import dev.zynema.user.dto.WatchHistoryDto;
import dev.zynema.user.dto.WatchlistItemDto;
import dev.zynema.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class UserQueryServiceTests extends AbstractUserIntegrationTest {

    private static final UUID DEMO_USER = UUID.fromString("71000000-0000-4000-8000-000000000001");
    private static final UUID DEMO_PROFILE = UUID.fromString("72000000-0000-4000-8000-000000000001");
    private static final UUID KIDS_PROFILE = UUID.fromString("72000000-0000-4000-8000-000000000002");

    @Autowired
    private UserQueryService queryService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("returns the seeded account with its profiles")
    void returnsSeededAccount() {
        UserDto user = queryService.getUser(DEMO_USER);

        assertThat(user.email()).isEqualTo("demo@zynema.dev");
        assertThat(user.displayName()).isEqualTo("Demo User");
        assertThat(user.profiles()).extracting(ProfileDto::name).containsExactly("Demo", "Kids");
        assertThat(user.profiles()).extracting(ProfileDto::kids).containsExactly(false, true);
    }

    @Test
    @DisplayName("finds an account by email")
    void findsByEmail() {
        assertThat(queryService.getUserByEmail("demo@zynema.dev").id()).isEqualTo(DEMO_USER);
    }

    @Test
    @DisplayName("unknown account or email raises ResourceNotFoundException")
    void unknownAccountRaisesNotFound() {
        assertThatThrownBy(() -> queryService.getUser(UUID.randomUUID()))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> queryService.getUserByEmail("nobody@zynema.dev"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("lists the profiles of an account")
    void listsProfiles() {
        assertThat(queryService.listProfiles(DEMO_USER)).hasSize(2);
    }

    @Test
    @DisplayName("lists the watchlist of the seeded profile, newest first")
    void listsWatchlist() {
        List<WatchlistItemDto> items = queryService.listWatchlist(DEMO_USER, DEMO_PROFILE);

        assertThat(items).hasSize(3);
        assertThat(items).isSortedAccordingTo((a, b) -> b.addedAt().compareTo(a.addedAt()));
    }

    @Test
    @DisplayName("lists history most recent first")
    void listsHistory() {
        List<WatchHistoryDto> history = queryService.listHistory(DEMO_USER, DEMO_PROFILE, 10);

        assertThat(history).hasSize(3);
        assertThat(history).isSortedAccordingTo((a, b) -> b.lastWatchedAt().compareTo(a.lastWatchedAt()));
        assertThat(history.get(0).contentId()).isEqualTo(UUID.fromString("a2000000-0000-4000-8000-000000000002"));
    }

    @Test
    @DisplayName("continue-watching returns only in-progress entries")
    void continueWatching() {
        List<WatchHistoryDto> inProgress = queryService.listContinueWatching(DEMO_USER, DEMO_PROFILE, 10);

        assertThat(inProgress).hasSize(2);
        assertThat(inProgress).allSatisfy(entry -> {
            assertThat(entry.completed()).isFalse();
            assertThat(entry.positionSeconds()).isPositive();
        });
    }

    @Test
    @DisplayName("activity of a foreign profile is not visible")
    void foreignProfileIsNotVisible() {
        User other = new User();
        other.setEmail("foreign@zynema.dev");
        UUID otherId = userRepository.save(other).getId();

        assertThatThrownBy(() -> queryService.listWatchlist(otherId, DEMO_PROFILE))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> queryService.listHistory(otherId, DEMO_PROFILE, 10))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> queryService.listContinueWatching(otherId, DEMO_PROFILE, 10))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("the kids profile has no activity")
    void kidsProfileIsEmpty() {
        assertThat(queryService.listWatchlist(DEMO_USER, KIDS_PROFILE)).isEmpty();
        assertThat(queryService.listContinueWatching(DEMO_USER, KIDS_PROFILE, 10)).isEmpty();
    }
}
