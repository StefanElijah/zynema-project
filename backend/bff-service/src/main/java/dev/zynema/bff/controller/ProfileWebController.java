package dev.zynema.bff.controller;

import dev.zynema.bff.dto.ProfileHomeView;
import dev.zynema.bff.service.ProfileHomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * The profile-scoped rails. Requires a token; ownership of the profile is
 * enforced by user-service, which is where the data lives.
 */
@RestController
@RequestMapping("/api/v1/web/profiles")
@RequiredArgsConstructor
@Tag(name = "Web BFF", description = "Aggregated views for the SPA")
public class ProfileWebController {

    private final ProfileHomeService profileHomeService;

    @GetMapping("/{profileId}/home")
    @Operation(summary = "Continue watching and my list for one profile, joined with the catalogue")
    public Mono<ProfileHomeView> profileHome(@PathVariable UUID profileId, @AuthenticationPrincipal Jwt jwt) {
        return profileHomeService.profileHome(profileId, jwt);
    }
}
