package dev.zynema.catalog.controller;

import dev.zynema.catalog.domain.ContentType;
import dev.zynema.catalog.dto.ContentDetailDto;
import dev.zynema.catalog.dto.ContentFilter;
import dev.zynema.catalog.dto.ContentSummaryDto;
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

@RestController
@RequestMapping("/api/v1/catalog/movies")
@RequiredArgsConstructor
@Validated
@Tag(name = "Catalog — Movies", description = "Browse the movie catalog")
public class MovieController {

    private final CatalogQueryService queryService;

    @GetMapping
    @Operation(summary = "List movies",
               description = "Paginated movie list with optional filters. Sorted by popularity by default.")
    public PageResponse<ContentSummaryDto> listMovies(
        @Parameter(description = "Genre slug, e.g. `sci-fi`") @RequestParam(required = false) String genre,
        @Parameter(description = "Inclusive lower bound for release year") @RequestParam(required = false) Integer yearFrom,
        @Parameter(description = "Inclusive upper bound for release year") @RequestParam(required = false) Integer yearTo,
        @Parameter(description = "Inclusive lower bound for average rating (0-10)") @RequestParam(required = false) BigDecimal minRating,
        @Parameter(description = "Sort as `field,direction`. Fields: popularity, rating, releaseYear, title")
        @RequestParam(defaultValue = "popularity,desc") String sort,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return queryService.listMovies(new ContentFilter(genre, yearFrom, yearTo, minRating), sort, page, size);
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get a movie by slug")
    public ContentDetailDto getMovie(@PathVariable String slug) {
        ContentDetailDto detail = queryService.getBySlug(slug);
        if (detail.type() != ContentType.MOVIE) {
            throw new ResourceNotFoundException("Movie", slug);
        }
        return detail;
    }
}
