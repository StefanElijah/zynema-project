package dev.zynema.user.service;

import dev.zynema.common.exception.BusinessRuleException;
import dev.zynema.common.exception.ResourceNotFoundException;
import dev.zynema.user.AbstractUserIntegrationTest;
import dev.zynema.user.domain.User;
import dev.zynema.user.dto.ProfileDto;
import dev.zynema.user.dto.UserDto;
import dev.zynema.user.dto.WatchHistoryDto;
import dev.zynema.user.dto.WatchProgressRequest;
import dev.zynema.user.dto.WatchlistItemDto;
import dev.zynema.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Command-side tests. Assertions rely on the V2 seed (one account, two
 * profiles, three watchlist items, three history rows).
 */
@Transactional
class UserCommandServiceTests extends AbstractUserIntegrationTest {

    private static final UUID DEMO_USER = UUID.fromString("71000000-0000-4000-8000-000000000001");
    private static final UUID DEMO_PROFILE = UUID.fromString("72000000-0000-4000-8000-000000000001");
    private static final UUID KIDS_PROFILE = UUID.fromString("72000000-0000-4000-8000-000000000002");
    private static final UUID DUNE_TWO = UUID.fromString("a1000000-0000-4000-8000-000000000003");

    @Autowired
    private UserCommandService commandService;

    @Autowired
    private UserQueryService queryService;

    @Autowired
    private UserRepository userRepository;

    // ──────────────────────────── profiles ────────────────────────────

    @Test
    @DisplayName("creates a profile for an account")
    void createsProfile() {
        ProfileDto profile = commandService.createProfile(DEMO_USER, new dev.zynema.user.dto.ProfileCreateRequest(
            "Guest", "avatar-03", true, "en"));

        assertThat(profile.name()).isEqualTo("Guest");
        assertThat(profile.kids()).isTrue();
        assertThat(profile.language()).isEqualTo("en");
        assertThat(queryService.listProfiles(DEMO_USER)).extracting(ProfileDto::name)
            .contains("Demo", "Kids", "Guest");
    }

    @Test
    @DisplayName("rejects a duplicate profile name in the same account")
    void rejectsDuplicateProfileName() {
        assertThatThrownBy(() -> commandService.createProfile(DEMO_USER,
            new dev.zynema.user.dto.ProfileCreateRequest("demo", null, false, null)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("rejects a profile for an unknown account")
    void rejectsProfileForUnknownAccount() {
        assertThatThrownBy(() -> commandService.createProfile(UUID.randomUUID(),
            new dev.zynema.user.dto.ProfileCreateRequest("Ghost", null, false, null)))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deletes a profile")
    void deletesProfile() {
        commandService.deleteProfile(DEMO_USER, KIDS_PROFILE);

        assertThat(queryService.listProfiles(DEMO_USER)).extracting(ProfileDto::name)
            .containsExactly("Demo");
    }

    @Test
    @DisplayName("cannot delete a profile through another account")
    void cannotDeleteForeignProfile() {
        User other = new User();
        other.setEmail("other@zynema.dev");
        UUID otherId = userRepository.save(other).getId();

        assertThatThrownBy(() -> commandService.deleteProfile(otherId, KIDS_PROFILE))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // ──────────────────────────── watchlist ───────────────────────────

    @Test
    @DisplayName("adds a title to the watchlist")
    void addsToWatchlist() {
        UUID interstellar = UUID.fromString("a1000000-0000-4000-8000-000000000008");

        WatchlistItemDto item = commandService.addToWatchlist(DEMO_USER, KIDS_PROFILE,
            new dev.zynema.user.dto.WatchlistAddRequest(interstellar));

        assertThat(item.contentId()).isEqualTo(interstellar);
        assertThat(queryService.listWatchlist(DEMO_USER, KIDS_PROFILE)).hasSize(1);
    }

    @Test
    @DisplayName("adding the same title twice is idempotent")
    void addToWatchlistIsIdempotent() {
        UUID content = UUID.fromString("a1000000-0000-4000-8000-000000000027");

        WatchlistItemDto first = commandService.addToWatchlist(DEMO_USER, DEMO_PROFILE,
            new dev.zynema.user.dto.WatchlistAddRequest(content));
        WatchlistItemDto second = commandService.addToWatchlist(DEMO_USER, DEMO_PROFILE,
            new dev.zynema.user.dto.WatchlistAddRequest(content));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(queryService.listWatchlist(DEMO_USER, DEMO_PROFILE)).hasSize(4);
    }

    @Test
    @DisplayName("removes a title from the watchlist")
    void removesFromWatchlist() {
        commandService.removeFromWatchlist(DEMO_USER, DEMO_PROFILE, DUNE_TWO);

        assertThat(queryService.listWatchlist(DEMO_USER, DEMO_PROFILE)).extracting(WatchlistItemDto::contentId)
            .doesNotContain(DUNE_TWO);
    }

    @Test
    @DisplayName("removing a title that is not in the watchlist raises 404")
    void removeUnknownWatchlistEntry() {
        assertThatThrownBy(() -> commandService.removeFromWatchlist(DEMO_USER, DEMO_PROFILE, UUID.randomUUID()))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // ──────────────────────────── progress ────────────────────────────

    @Test
    @DisplayName("records movie progress and updates the existing row on retry")
    void recordsMovieProgressIdempotently() {
        WatchProgressRequest request = new WatchProgressRequest(DUNE_TWO, null, 1200, 9960, false);

        WatchHistoryDto first = commandService.recordProgress(DEMO_USER, DEMO_PROFILE, request);
        WatchHistoryDto second = commandService.recordProgress(DEMO_USER, DEMO_PROFILE,
            new WatchProgressRequest(DUNE_TWO, null, 1500, 9960, false));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.positionSeconds()).isEqualTo(1500);
        assertThat(queryService.listHistory(DEMO_USER, DEMO_PROFILE, 50)).hasSize(3);
    }

    @Test
    @DisplayName("records episode progress as its own row")
    void recordsEpisodeProgress() {
        UUID content = UUID.fromString("a2000000-0000-4000-8000-000000000002");
        UUID episode = UUID.fromString("e1000000-0000-4000-8000-000000000009");

        WatchHistoryDto dto = commandService.recordProgress(DEMO_USER, DEMO_PROFILE,
            new WatchProgressRequest(content, episode, 300, 2400, false));

        assertThat(dto.episodeId()).isEqualTo(episode);
        assertThat(queryService.listHistory(DEMO_USER, DEMO_PROFILE, 50)).hasSize(4);
    }

    @Test
    @DisplayName("marks a title completed at 95% of its duration")
    void marksCompletedAt95Percent() {
        WatchHistoryDto dto = commandService.recordProgress(DEMO_USER, DEMO_PROFILE,
            new WatchProgressRequest(DUNE_TWO, null, 9462, 9960, false));

        assertThat(dto.completed()).isTrue();
    }

    @Test
    @DisplayName("continue-watching excludes completed titles")
    void continueWatchingExcludesCompleted() {
        assertThat(queryService.listContinueWatching(DEMO_USER, DEMO_PROFILE, 10))
            .extracting(WatchHistoryDto::contentId)
            .doesNotContain(UUID.fromString("a1000000-0000-4000-8000-000000000008"));
    }

    @Test
    @DisplayName("cannot record progress through another account")
    void cannotRecordProgressForForeignProfile() {
        User other = new User();
        other.setEmail("other2@zynema.dev");
        UUID otherId = userRepository.save(other).getId();

        assertThatThrownBy(() -> commandService.recordProgress(otherId, DEMO_PROFILE,
            new WatchProgressRequest(DUNE_TWO, null, 10, 100, false)))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // ────────────────────────────── users ─────────────────────────────

    @Test
    @DisplayName("creates an account and rejects a duplicate email")
    void createsUserAndRejectsDuplicateEmail() {
        UserDto created = commandService.createUser(new dev.zynema.user.dto.UserCreateRequest(
            "new@zynema.dev", "New User", null, "en"));

        assertThat(created.id()).isNotNull();
        assertThat(created.preferredLanguage()).isEqualTo("en");

        assertThatThrownBy(() -> commandService.createUser(new dev.zynema.user.dto.UserCreateRequest(
            "new@zynema.dev", null, null, null)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("already registered");
    }
}
