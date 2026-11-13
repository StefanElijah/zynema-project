package dev.zynema.catalog.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.catalog.dto.ContentSummaryDto;
import dev.zynema.catalog.dto.FranchiseDetailDto;
import dev.zynema.catalog.dto.FranchiseSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read side of sagas. The ordering is explicit in the write table, and the
 * member cards come from the same content projection every other list uses.
 */
@Repository
@RequiredArgsConstructor
public class FranchiseReadRepository {

    private static final String SELECT_SUMMARIES = """
        SELECT f.id, f.slug, f.name, f.description, f.cover_url,
               count(fc.content_id) AS item_count
        FROM franchises f
        LEFT JOIN franchise_contents fc ON fc.franchise_id = f.id
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public List<FranchiseSummaryDto> findAll() {
        return jdbc.query(SELECT_SUMMARIES + """
            GROUP BY f.id
            ORDER BY f.sort_order, f.name
            """, new MapSqlParameterSource(), (rs, rowNum) -> summary(rs));
    }

    public Optional<FranchiseDetailDto> findBySlug(String slug) {
        List<FranchiseSummaryDto> rows = jdbc.query(SELECT_SUMMARIES + """
            WHERE f.slug = :slug
            GROUP BY f.id
            """, new MapSqlParameterSource("slug", slug), (rs, rowNum) -> summary(rs));
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        FranchiseSummaryDto summary = rows.get(0);
        List<String> payloads = jdbc.queryForList("""
            SELECT rm.summary
            FROM franchise_contents fc
            JOIN content_read_model rm ON rm.content_id = fc.content_id
            WHERE fc.franchise_id = :id
            ORDER BY fc.position
            """, Map.of("id", summary.id()), String.class);

        return Optional.of(new FranchiseDetailDto(
            summary.id(), summary.slug(), summary.name(), summary.description(), summary.coverUrl(),
            payloads.stream().map(this::read).toList()));
    }

    private FranchiseSummaryDto summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FranchiseSummaryDto(
            rs.getObject("id", UUID.class),
            rs.getString("slug"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("cover_url"),
            rs.getLong("item_count"));
    }

    private ContentSummaryDto read(String json) {
        try {
            return objectMapper.readValue(json, ContentSummaryDto.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Unreadable read-model payload", ex);
        }
    }
}
