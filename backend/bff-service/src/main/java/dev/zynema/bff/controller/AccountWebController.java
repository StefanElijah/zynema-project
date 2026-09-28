package dev.zynema.bff.controller;

import dev.zynema.bff.dto.AccountView;
import dev.zynema.bff.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * The account screen. Requires a token; the identity comes from it.
 */
@RestController
@RequestMapping("/api/v1/web")
@RequiredArgsConstructor
@Tag(name = "Web BFF", description = "Aggregated views for the SPA")
public class AccountWebController {

    private final AccountService accountService;

    @GetMapping("/account")
    @Operation(summary = "Identity, local account and subscription in one view")
    public Mono<AccountView> account(@AuthenticationPrincipal Jwt jwt) {
        return accountService.account(jwt);
    }
}
