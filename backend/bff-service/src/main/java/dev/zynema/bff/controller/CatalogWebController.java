package dev.zynema.bff.controller;

import dev.zynema.bff.client.CatalogClient;
import dev.zynema.bff.client.DownstreamErrors;
import dev.zynema.bff.client.DownstreamPage;
import dev.zynema.bff.dto.ContentKind;
import dev.zynema.bff.dto.ContentView;
import dev.zynema.bff.dto.TitleCard;
import dev.zynema.bff.service.ContentService;
import dev.zynema.bff.service.UpstreamGateway;
import dev.zynema.bff.service.ViewMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * The catalogue as the SPA sees it: browse, search and detail.
 *
 * <p>Public, because the metadata is the shop window. In the detail the token is
 * <em>optional</em>: when it is there, the answer says whether this account may
 * watch and where the profile left off; when it is not, the same payload is
 * served without the context. That is why the JWT is read from the Reactor
 * context instead of an {@code @AuthenticationPrincipal} parameter — the
 * endpoint must work for both callers.
 *
 * <p>Browse and search are thin lists over the catalogue: the tracer and this
 * layer decide nothing about content, and the catalogue's own cache absorbs the
 * repeated reads, so there is no second cache here.
 */
@RestController
@RequestMapping("/api/v1/web/catalog")
@RequiredArgsConstructor
@Tag(name = "Web BFF", description = "Aggregated views for the SPA")
public class CatalogWebController {

    private static final int MAX_PAGE_SIZE = 50;

    private final ContentService contentService;
    private final UpstreamGateway gateway;

    @GetMapping
    @Operation(summary = "Browse the catalogue with the caller's filters")
    public Mono<DownstreamPage<TitleCard>> browse(@RequestParam ContentKind type,
                                                  @RequestParam(required = false) String genre,
                                                  @RequestParam(required = false) Integer yearFrom,
                                                  @RequestParam(required = false) Integer yearTo,
                                                  @RequestParam(required = false) BigDecimal minRating,
                                                  @RequestParam(defaultValue = "popularity,desc") String sort,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "24") int size) {
        return gateway.browse(type, new CatalogClient.ContentFilter(genre, yearFrom, yearTo, minRating),
                sort, page, Math.min(size, MAX_PAGE_SIZE))
            .map(result -> new DownstreamPage<>(
                result.content().stream().map(ViewMapper::card).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages(),
                result.first(), result.last()))
            .transform(publisher -> DownstreamErrors.toApiError("catalog-service", publisher));
    }

    @GetMapping("/search")
    @Operation(summary = "Search titles by name")
    public Mono<DownstreamPage<TitleCard>> search(@RequestParam String q,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "24") int size) {
        return gateway.search(q, page, Math.min(size, MAX_PAGE_SIZE))
            .map(result -> new DownstreamPage<>(
                result.content().stream().map(ViewMapper::card).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages(),
                result.first(), result.last()))
            .transform(publisher -> DownstreamErrors.toApiError("catalog-service", publisher));
    }

    @GetMapping("/{idOrSlug}")
    @Operation(summary = "Content detail plus the caller's playback context, when there is one")
    public Mono<ContentView> content(@PathVariable String idOrSlug,
                                     @RequestParam(required = false) UUID profileId) {
        return currentJwt().flatMap(jwt -> contentService.content(idOrSlug, profileId, jwt.orElse(null)));
    }

    private Mono<Optional<Jwt>> currentJwt() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(JwtAuthenticationToken.class::isInstance)
            .cast(JwtAuthenticationToken.class)
            .map(JwtAuthenticationToken::getToken)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());
    }
}
