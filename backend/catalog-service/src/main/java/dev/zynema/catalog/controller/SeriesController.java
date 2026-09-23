package dev.zynema.catalog.controller;

import dev.zynema.catalog.domain.ContentType;
import dev.zynema.catalog.dto.ContentDetailDto;
import dev.zynema.catalog.dto.ContentFilter;
import dev.zynema.catalog.dto.ContentSummaryDto;
import dev.zynema.catalog.dto.EpisodeDto;
import dev.zynema.catalog.service.CatalogQueryService;
import dev.zynema.common.dto.PageResponse;
import dev.zynema.common.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog/series")
@RequiredArgsConstructor
@Validated
@Tag(name = "Catalog — Series", description = "Browse the series catalog")
public class SeriesController {

    private final CatalogQueryService queryService;

    @GetMapping
    @Operation(summary = "List series",
               description = "Paginated series list with optional filters. Sorted by popularity by default.")
    public PageResponse<ContentSummaryDto> listSeries(
        @Parameter(description = "Genre slug, e.g. `drama`") @RequestParam(required = false) String genre,
        @Parameter(description = "Inclusive lower bound for release year") @RequestParam(required = false) Integer yearFrom,
        @Parameter(description = "Inclusive upper bound for release year") @RequestParam(required = false) Integer yearTo,
        @Parameter(description = "Inclusive lower bound for average rating (0-10)") @RequestParam(required = false) BigDecimal minRating,
        @Parameter(description = "Sort as `field,direction`. Fields: popularity, rating, releaseYear, title")
        @RequestParam(defaultValue = "popularity,desc") String sort,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return queryService.listSeries(new ContentFilter(genre, yearFrom, yearTo, minRating), sort, page, size);
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get a series by slug", description = "Includes the season list with episode counts.")
    public ContentDetailDto getSeries(@PathVariable String slug) {
        ContentDetailDto detail = queryService.getBySlug(slug);
        if (detail.type() != ContentType.SERIES) {
            throw new ResourceNotFoundException("Series", slug);
        }
        return detail;
    }

    @GetMapping("/{slug}/seasons/{seasonNumber}/episodes")
    @Operation(summary = "List the episodes of a season")
    public List<EpisodeDto> listEpisodes(
        @PathVariable String slug,
        @Parameter(description = "Season number, 1-based") @PathVariable int seasonNumber
    ) {
        return queryService.listEpisodes(slug, seasonNumber);
    }
}
