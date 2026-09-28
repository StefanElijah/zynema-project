package dev.zynema.bff.controller;

import dev.zynema.bff.dto.ContentView;
import dev.zynema.bff.service.ContentService;
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

import java.util.Optional;
import java.util.UUID;

/**
 * Content detail with user context.
 *
 * <p>Public, because the metadata is the shop window. The token is
 * <em>optional</em>: when it is there, the answer says whether this account may
 * watch and where the profile left off; when it is not, the same payload is
 * served without the context. That is why the JWT is read from the Reactor
 * context instead of an {@code @AuthenticationPrincipal} parameter — the
 * endpoint must work for both callers.
 */
@RestController
@RequestMapping("/api/v1/web/catalog")
@RequiredArgsConstructor
@Tag(name = "Web BFF", description = "Aggregated views for the SPA")
public class CatalogWebController {

    private final ContentService contentService;

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
