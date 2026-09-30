package dev.zynema.catalog.repository;

import dev.zynema.catalog.domain.Season;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonRepository extends JpaRepository<Season, UUID> {

    Optional<Season> findByContentIdAndSeasonNumber(UUID contentId, Integer seasonNumber);

    List<Season> findByContentIdOrderBySeasonNumberAsc(UUID contentId);

    /**
     * Season list with episode counts computed in the database: avoids
     * loading every episode just to render a season picker.
     */
    @Query("""
        SELECT s.id AS id,
               s.seasonNumber AS seasonNumber,
               s.title AS title,
               s.synopsis AS synopsis,
               s.releaseYear AS releaseYear,
               s.posterUrl AS posterUrl,
               SIZE(s.episodes) AS episodeCount
        FROM Season s
        WHERE s.content.id = :contentId
        ORDER BY s.seasonNumber ASC
        """)
    List<SeasonSummaryProjection> findSeasonSummaries(@Param("contentId") UUID contentId);

    interface SeasonSummaryProjection {
        UUID getId();
        Integer getSeasonNumber();
        String getTitle();
        String getSynopsis();
        Integer getReleaseYear();
        String getPosterUrl();
        Integer getEpisodeCount();
    }
}
