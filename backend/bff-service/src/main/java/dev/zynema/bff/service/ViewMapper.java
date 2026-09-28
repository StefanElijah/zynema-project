package dev.zynema.bff.service;

import dev.zynema.bff.client.CatalogClient;
import dev.zynema.bff.dto.GenreRef;
import dev.zynema.bff.dto.TitleCard;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Translates the wire format of catalog-service into the BFF's own view models.
 *
 * <p>Explicit instead of reflective: the day catalog-service renames a field,
 * this file stops compiling or a test fails, rather than the frontend silently
 * receiving a null. It is the only place that knows both shapes.
 */
final class ViewMapper {

    private ViewMapper() {
    }

    static TitleCard card(CatalogClient.ContentSummary summary) {
        return new TitleCard(
            summary.id(),
            summary.type(),
            summary.slug(),
            summary.title(),
            summary.posterUrl(),
            summary.backdropUrl(),
            summary.releaseYear(),
            summary.maturityRating(),
            summary.averageRating(),
            genres(summary.genres()));
    }

    static TitleCard card(CatalogClient.ContentDetail detail) {
        return new TitleCard(
            detail.id(),
            detail.type(),
            detail.slug(),
            detail.title(),
            detail.posterUrl(),
            detail.backdropUrl(),
            detail.releaseYear(),
            detail.maturityRating(),
            detail.averageRating(),
            genres(detail.genres()));
    }

    static List<GenreRef> genres(List<CatalogClient.Genre> genres) {
        return genres == null ? List.of()
            : genres.stream().map(g -> new GenreRef(g.slug(), g.name())).toList();
    }

    /**
     * Newest first across movies and series: the catalogue has no "recently
     * added" flag, and a rail that mixes both is what the screen wants anyway.
     */
    static List<TitleCard> newestFirst(List<CatalogClient.ContentSummary> movies,
                                       List<CatalogClient.ContentSummary> series,
                                       int limit) {
        return java.util.stream.Stream.concat(movies.stream(), series.stream())
            .filter(summary -> summary.releaseYear() != null)
            .sorted(Comparator.comparing(CatalogClient.ContentSummary::releaseYear).reversed()
                .thenComparing(CatalogClient.ContentSummary::title,
                    Comparator.comparing(Objects::toString, String.CASE_INSENSITIVE_ORDER)))
            .limit(limit)
            .map(ViewMapper::card)
            .toList();
    }
}
