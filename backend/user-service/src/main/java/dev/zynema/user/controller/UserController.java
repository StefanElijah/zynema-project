package dev.zynema.user.controller;

import dev.zynema.user.dto.UserCreateRequest;
import dev.zynema.user.dto.UserDto;
import dev.zynema.user.service.UserCommandService;
import dev.zynema.user.service.UserQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "Users", description = "Accounts and viewer profiles")
public class UserController {

    private final UserQueryService queryService;
    private final UserCommandService commandService;

    @GetMapping("/{userId}")
    @Operation(summary = "Get an account by id", description = "Includes its viewer profiles.")
    public UserDto getUser(@PathVariable UUID userId) {
        return queryService.getUser(userId);
    }

    @GetMapping(params = "email")
    @Operation(summary = "Get an account by email")
    public UserDto getUserByEmail(
        @Parameter(description = "Account email") @RequestParam @Email String email
    ) {
        return queryService.getUserByEmail(email);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account",
               description = "Usually called by the identity sync once Keycloak is wired (Fase 3).")
    public UserDto createUser(@Valid @RequestBody UserCreateRequest request) {
        return commandService.createUser(request);
    }
}
