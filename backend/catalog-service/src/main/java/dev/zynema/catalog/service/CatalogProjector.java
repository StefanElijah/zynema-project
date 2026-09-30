package dev.zynema.catalog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.catalog.domain.Content;
import dev.zynema.catalog.domain.ContentType;
import dev.zynema.catalog.domain.Season;
import dev.zynema.catalog.dto.ContentDetailDto;
import dev.zynema.catalog.dto.ContentSummaryDto;
import dev.zynema.catalog.dto.EpisodeDto;
import dev.zynema.catalog.dto.SeasonDto;
import dev.zynema.catalog.mapper.ContentMapper;
import dev.zynema.catalog.mapper.CreditMapper;
import dev.zynema.catalog.mapper.EpisodeMapper;
import dev.zynema.catalog.mapper.SeasonMapper;
import dev.zynema.catalog.repository.ContentRepository;
import dev.zynema.catalog.repository.CreditRepository;
import dev.zynema.catalog.repository.EpisodeRepository;
import dev.zynema.catalog.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns the write model into the read model (ADR-0006, ADR-0022).
 *
 * <p>Runs <strong>inside the writer's transaction</strong>, not after commit.
 * The projection is then atomic with the write: a rolled-back update leaves no
 * projected row behind, a crash cannot lose a projection, and a test (or a
 * client reading its own write) sees a consistent pair. The read model is a
 * <em>cache of a shape</em>, not a second source of truth — the write tables
 * are still the only thing that has to be right.
 *
 * <p>The cost is that every write also rebuilds one row: a handful of indexed
 * reads and one upsert per title. Catalogue writes are rare compared to reads,
 * which is the trade CQRS is making here.
 */
@Service
@RequiredArgsConstructor
public class CatalogProjector {

    private static final String UPSERT = """
        INSERT INTO content_read_model
            (content_id, slug, type, status, title, release_year, maturity_rating, average_rating,
             popularity, genre_slugs, summary, detail, episodes, episodes_by_id, search_vector, updated_at)
        VALUES
            (:contentId, :slug, :type, :status, :title, :releaseYear, :maturityRating, :averageRating,
             :popularity, CAST(:genreSlugs AS jsonb), CAST(:summary AS jsonb), CAST(:detail AS jsonb),
             CAST(:episodes AS jsonb), CAST(:episodesById AS jsonb), to_tsvector('simple', :title), now())
        ON CONFLICT (content_id) DO UPDATE SET
            slug = EXCLUDED.slug,
            type = EXCLUDED.type,
            status = EXCLUDED.status,
            title = EXCLUDED.title,
            release_year = EXCLUDED.release_year,
            maturity_rating = EXCLUDED.maturity_rating,
            average_rating = EXCLUDED.average_rating,
            popularity = EXCLUDED.popularity,
            genre_slugs = EXCLUDED.genre_slugs,
            summary = EXCLUDED.summary,
            detail = EXCLUDED.detail,
            episodes = EXCLUDED.episodes,
            episodes_by_id = EXCLUDED.episodes_by_id,
            search_vector = EXCLUDED.search_vector,
            updated_at = now()
        """;

    private final ContentRepository contentRepository;
    private final SeasonRepository seasonRepository;
    private final EpisodeRepository episodeRepository;
    private final CreditRepository creditRepository;
    private final ContentMapper contentMapper;
    private final SeasonMapper seasonMapper;
    private final EpisodeMapper episodeMapper;
    private final CreditMapper creditMapper;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /**
     * Rebuilds one title's row. The caller must have flushed the entity: this
     * reads the write tables through JDBC, which does not see un-flushed JPA
     * state.
     *
     * @return the detail payload, so the write side can answer with the same
     *         data it just projected instead of reading it back
     */
    public ContentDetailDto project(Content content) {
        ContentSummaryDto summary = contentMapper.toSummary(content);
        ContentDetailDto detail = assembleDetail(content);
        EpisodePayloads episodes = episodesOf(content);

        jdbc.update(UPSERT, new MapSqlParameterSource()
            .addValue("contentId", content.getId())
            .addValue("slug", content.getSlug())
            .addValue("type", content.getType().name())
            .addValue("status", content.getStatus().name())
            .addValue("title", content.getTitle())
            .addValue("releaseYear", content.getReleaseYear())
            .addValue("maturityRating", content.getMaturityRating())
            .addValue("averageRating", content.getAverageRating())
            .addValue("popularity", content.getPopularity())
            .addValue("genreSlugs", json(genreSlugs(content)))
            .addValue("summary", json(summary))
            .addValue("detail", json(detail))
            .addValue("episodes", json(episodes.bySeason()))
            .addValue("episodesById", json(episodes.byId())));
        return detail;
    }

    public void remove(UUID contentId) {
        jdbc.update("DELETE FROM content_read_model WHERE content_id = :id",
            Map.of("id", contentId));
    }

    /**
     * Full rebuild, used to backfill an empty projection (first boot after this
     * migration, or a corrupted model). Idempotent by construction: it only
     * writes what the write model currently says.
     */
    public int rebuildAll() {
        List<UUID> ids = contentRepository.findAll().stream().map(Content::getId).toList();
        if (ids.isEmpty()) {
            return 0;
        }
        contentRepository.findAllWithGenresByIdIn(ids).forEach(this::project);
        return ids.size();
    }

    // ───────────────────────────── helpers ────────────────────────────

    private ContentDetailDto assembleDetail(Content content) {
        List<SeasonDto> seasons = content.getType() == ContentType.SERIES
            ? seasonRepository.findSeasonSummaries(content.getId()).stream().map(seasonMapper::toDto).toList()
            : List.of();

        return contentMapper.toDetail(content).toBuilder()
            .seasons(seasons)
            .credits(creditMapper.toDtoList(
                creditRepository.findByContentIdOrderByBillingOrderAsc(content.getId())))
            .build();
    }

    /**
     * Two shapes of the same data: by season for the player's episode picker,
     * and flat by id for playback, which holds an episode id and nothing else.
     */
    private EpisodePayloads episodesOf(Content content) {
        if (content.getType() != ContentType.SERIES) {
            return new EpisodePayloads(Map.of(), Map.of());
        }
        Map<String, List<EpisodeDto>> bySeason = new LinkedHashMap<>();
        Map<String, EpisodeDto> byId = new LinkedHashMap<>();
        for (Season season : seasonRepository.findByContentIdOrderBySeasonNumberAsc(content.getId())) {
            List<EpisodeDto> episodes = episodeMapper.toDtoList(
                episodeRepository.findBySeasonIdOrderByEpisodeNumberAsc(season.getId()));
            bySeason.put(String.valueOf(season.getSeasonNumber()), episodes);
            episodes.forEach(episode -> byId.put(String.valueOf(episode.id()), episode));
        }
        return new EpisodePayloads(bySeason, byId);
    }

    private record EpisodePayloads(Map<String, List<EpisodeDto>> bySeason, Map<String, EpisodeDto> byId) {
    }

    private List<String> genreSlugs(Content content) {
        return content.getGenres().stream().map(genre -> genre.getSlug()).sorted().toList();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise a read-model payload", ex);
        }
    }
}
