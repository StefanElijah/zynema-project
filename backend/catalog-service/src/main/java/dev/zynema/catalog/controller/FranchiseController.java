package dev.zynema.catalog.controller;

import dev.zynema.catalog.dto.FranchiseDetailDto;
import dev.zynema.catalog.dto.FranchiseSummaryDto;
import dev.zynema.catalog.service.FranchiseQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sagas/franchises: curated, ordered groups of movies and series.
 * Public like the rest of the catalogue metadata.
 */
@RestController
@RequestMapping("/api/v1/catalog/franchises")
@RequiredArgsConstructor
@Tag(name = "Catalog — Franchises", description = "Curated sagas and their ordered titles")
public class FranchiseController {

    private final FranchiseQueryService queryService;

    @GetMapping
    @Operation(summary = "All sagas with their cover and item count")
    public List<FranchiseSummaryDto> franchises() {
        return queryService.list();
    }

    @GetMapping("/{slug}")
    @Operation(summary = "A saga and its titles, ordered by the curated position")
    public FranchiseDetailDto franchise(@PathVariable String slug) {
        return queryService.get(slug);
    }
}
