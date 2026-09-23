package dev.zynema.catalog.repository;

import dev.zynema.catalog.domain.Episode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EpisodeRepository extends JpaRepository<Episode, UUID> {

    List<Episode> findBySeasonIdOrderByEpisodeNumberAsc(UUID seasonId);
}
