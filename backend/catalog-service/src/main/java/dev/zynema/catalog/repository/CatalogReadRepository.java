package dev.zynema.catalog.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.catalog.domain.ContentType;
import dev.zynema.catalog.dto.ContentDetailDto;
import dev.zynema.catalog.dto.ContentFilter;
import dev.zynema.catalog.dto.ContentSummaryDto;
import dev.zynema.catalog.dto.EpisodeDto;
import dev.zynema.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The read side of the catalogue (ADR-0006, ADR-0022).
 *
 * <p>SQL over the projection instead of JPA over the aggregates: the query side
 * never needs the write model's invariants, and a denormalised row is a single
 * lookup instead of a fan-out across genres, seasons and credits.
 *
 * <p>Two conversions happen here and nowhere else: the sort parameter becomes a
 * column of a whitelist (never interpolated from input, so no injection through
 * ordering) and the JSONB payloads come back as the same DTOs the old JPA path
 * produced — the HTTP contract does not move.
 */
@Repository
@RequiredArgsConstructor
public class CatalogReadRepository {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
        "popularity", "popularity",
        "rating", "average_rating",
        "releaseYear", "release_year",
        "title", "title");

    private static final String SELECT_SUMMARY = "SELECT summary FROM content_read_model";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PageResponse<ContentSummaryDto> findPage(ContentType type, ContentFilter filter, String sort,
                                                    int page, int size) {
        ContentFilter effective = filter == null ? ContentFilter.empty() : filter;
        MapSqlParameterSource params = new MapSqlParameterSource("type", type.name());
        StringBuilder where = new StringBuilder(" WHERE type = :type AND status = 'PUBLISHED'");

        if (effective.genre() != null) {
            // JSON containment, indexable by the GIN index on the column.
            where.append(" AND genre_slugs @> CAST(:genre AS jsonb)");
            params.addValue("genre", "[\"%s\"]".formatted(effective.genre()));
        }
        if (effective.yearFrom() != null) {
            where.append(" AND release_year >= :yearFrom");
            params.addValue("yearFrom", effective.yearFrom());
        }
        if (effective.yearTo() != null) {
            where.append(" AND release_year <= :yearTo");
            params.addValue("yearTo", effective.yearTo());
        }
        if (effective.minRating() != null) {
            where.append(" AND average_rating >= :minRating");
            params.addValue("minRating", effective.minRating());
        }

        String sql = SELECT_SUMMARY + where + orderBy(sort) + " LIMIT :size OFFSET :offset";
        params.addValue("size", size).addValue("offset", (long) page * size);

        List<ContentSummaryDto> content = jdbc.query(sql, params,
            (rs, rowNum) -> read(rs.getString("summary"), ContentSummaryDto.class));
        return page(content, page, size, count(where.toString(), params));
    }

    /**
     * Title search, same semantics as the original native query: a plain
     * to_tsquery against the title, most popular first.
     */
    public PageResponse<ContentSummaryDto> search(String query, int page, int size) {
        String where = " WHERE status = 'PUBLISHED' AND search_vector @@ plainto_tsquery('simple', :query)";
        MapSqlParameterSource params = new MapSqlParameterSource("query", query)
            .addValue("size", size)
            .addValue("offset", (long) page * size);

        List<ContentSummaryDto> content = jdbc.query(
            SELECT_SUMMARY + where + " ORDER BY popularity DESC, content_id ASC LIMIT :size OFFSET :offset",
            params, (rs, rowNum) -> read(rs.getString("summary"), ContentSummaryDto.class));

        MapSqlParameterSource countParams = new MapSqlParameterSource("query", query);
        return page(content, page, size, count(where, countParams));
    }

    public Optional<ContentDetailDto> findDetailBySlug(String slug) {
        return findDetail("slug = :value", slug);
    }

    public Optional<ContentDetailDto> findDetailById(UUID id) {
        return findDetail("content_id = :value", id);
    }

    /**
     * @return empty when the season is not part of the title; an empty list is
     *         a season that exists and has no episodes yet
     */
    public Optional<List<EpisodeDto>> findEpisodes(String slug, int seasonNumber) {
        List<String> payloads = jdbc.queryForList(
            "SELECT episodes FROM content_read_model WHERE slug = :slug",
            Map.of("slug", slug), String.class);
        if (payloads.isEmpty()) {
            return Optional.empty();
        }
        Map<String, List<EpisodeDto>> bySeason =
            read(payloads.get(0), new TypeReference<Map<String, List<EpisodeDto>>>() {
            });
        return Optional.ofNullable(bySeason.get(String.valueOf(seasonNumber)));
    }

    /**
     * The flat episode index: playback holds an episode id and needs its HLS
     * path without walking the season-shaped payload.
     */
    public Optional<EpisodeDto> findEpisodeById(UUID episodeId) {
        List<String> payloads = jdbc.queryForList(
            "SELECT episodes_by_id -> :id FROM content_read_model WHERE episodes_by_id -> :id IS NOT NULL",
            new MapSqlParameterSource("id", episodeId.toString()), String.class);
        return payloads.stream().findFirst().map(json -> read(json, EpisodeDto.class));
    }

    public long count() {
        Long total = jdbc.queryForObject("SELECT count(*) FROM content_read_model",
            new MapSqlParameterSource(), Long.class);
        return total == null ? 0 : total;
    }

    // ───────────────────────────── helpers ────────────────────────────

    private Optional<ContentDetailDto> findDetail(String condition, Object value) {
        List<String> payloads = jdbc.queryForList(
            "SELECT detail FROM content_read_model WHERE " + condition,
            new MapSqlParameterSource("value", value), String.class);
        return payloads.stream().findFirst().map(json -> read(json, ContentDetailDto.class));
    }

    private long count(String where, MapSqlParameterSource params) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM content_read_model" + where, params, Long.class);
        return total == null ? 0 : total;
    }

    private String orderBy(String sort) {
        String[] parts = sort == null ? new String[0] : sort.split(",");
        String column = SORT_COLUMNS.getOrDefault(parts.length > 0 ? parts[0] : "popularity", "popularity");
        String direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1]) ? "ASC" : "DESC";
        // Secondary key keeps pagination stable when the primary key ties,
        // mirroring the id tiebreak the JPA path added.
        return " ORDER BY %s %s, content_id ASC".formatted(column, direction);
    }

    private PageResponse<ContentSummaryDto> page(List<ContentSummaryDto> content, int page, int size, long total) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, page, size, total, totalPages,
            page == 0, totalPages > 0 && page >= totalPages - 1);
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Unreadable read-model payload", ex);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Unreadable read-model payload", ex);
        }
    }
}
