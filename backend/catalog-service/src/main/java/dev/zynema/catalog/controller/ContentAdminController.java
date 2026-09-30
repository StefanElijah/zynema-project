package dev.zynema.catalog.controller;

import dev.zynema.catalog.dto.ContentCreateRequest;
import dev.zynema.catalog.dto.ContentDetailDto;
import dev.zynema.catalog.dto.ContentUpdateRequest;
import dev.zynema.catalog.service.CatalogCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin write API. Anonymous in Phase 2; role-protected from Phase 3
 * ({@code content-manager}).
 */
@RestController
@RequestMapping("/api/v1/catalog/admin/contents")
@RequiredArgsConstructor
@Tag(name = "Catalog — Admin", description = "Create, update and delete catalog entries")
public class ContentAdminController {

    private final CatalogCommandService commandService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a catalog entry")
    public ContentDetailDto create(@Valid @RequestBody ContentCreateRequest request) {
        return commandService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a catalog entry", description = "Slug and type are immutable.")
    public ContentDetailDto update(@PathVariable UUID id, @Valid @RequestBody ContentUpdateRequest request) {
        return commandService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a catalog entry")
    public void delete(@PathVariable UUID id) {
        commandService.delete(id);
    }
}
