package dev.zynema.user.controller;

import dev.zynema.user.dto.ProfileCreateRequest;
import dev.zynema.user.dto.ProfileDto;
import dev.zynema.user.service.UserCommandService;
import dev.zynema.user.service.UserQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}/profiles")
@RequiredArgsConstructor
@Tag(name = "Profiles", description = "Viewer profiles inside an account")
public class ProfileController {

    private final UserQueryService queryService;
    private final UserCommandService commandService;

    @GetMapping
    @Operation(summary = "List the profiles of an account")
    public List<ProfileDto> listProfiles(@PathVariable UUID userId) {
        return queryService.listProfiles(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a profile")
    public ProfileDto createProfile(@PathVariable UUID userId, @Valid @RequestBody ProfileCreateRequest request) {
        return commandService.createProfile(userId, request);
    }

    @DeleteMapping("/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a profile", description = "Cascades to its watchlist and history.")
    public void deleteProfile(@PathVariable UUID userId, @PathVariable UUID profileId) {
        commandService.deleteProfile(userId, profileId);
    }
}
