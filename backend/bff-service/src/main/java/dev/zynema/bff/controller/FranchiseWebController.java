package dev.zynema.bff.controller;

import dev.zynema.bff.client.DownstreamErrors;
import dev.zynema.bff.dto.FranchiseCard;
import dev.zynema.bff.dto.FranchiseView;
import dev.zynema.bff.service.FranchiseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Sagas/franchises for the SPA: public like the catalogue they group.
 */
@RestController
@RequestMapping("/api/v1/web/franchises")
@RequiredArgsConstructor
@Tag(name = "Web BFF", description = "Aggregated views for the SPA")
public class FranchiseWebController {

    private final FranchiseService franchiseService;

    @GetMapping
    @Operation(summary = "All sagas with their cover and item count")
    public Mono<List<FranchiseCard>> franchises() {
        return franchiseService.list()
            .transform(publisher -> DownstreamErrors.toApiError("catalog-service", publisher));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "A saga and its titles in curated order")
    public Mono<FranchiseView> franchise(@PathVariable String slug) {
        return franchiseService.detail(slug)
            .transform(publisher -> DownstreamErrors.toApiError("catalog-service", publisher));
    }
}
