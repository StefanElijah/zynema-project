package dev.zynema.catalog.controller;

import dev.zynema.catalog.dto.ContentSummaryDto;
import dev.zynema.catalog.service.CatalogQueryService;
import dev.zynema.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog/search")
@RequiredArgsConstructor
@Validated
@Tag(name = "Catalog — Search", description = "Full-text search across movies and series")
public class SearchController {

    private final CatalogQueryService queryService;

    @GetMapping
    @Operation(summary = "Search titles",
               description = "PostgreSQL full-text search over title and original title, ranked by popularity.")
    public PageResponse<ContentSummaryDto> search(
        @Parameter(description = "Search terms, e.g. `dune`") @RequestParam @NotBlank String q,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return queryService.search(q, page, size);
    }
}
