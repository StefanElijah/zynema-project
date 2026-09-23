package dev.zynema.user.repository;

import dev.zynema.user.domain.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WatchlistRepository extends JpaRepository<WatchlistItem, UUID> {

    List<WatchlistItem> findByProfileIdOrderByAddedAtDesc(UUID profileId);

    Optional<WatchlistItem> findByProfileIdAndContentId(UUID profileId, UUID contentId);

    boolean existsByProfileIdAndContentId(UUID profileId, UUID contentId);

    void deleteByProfileIdAndContentId(UUID profileId, UUID contentId);
}
