package dev.zynema.payment.controller;

import dev.zynema.payment.dto.SubscribeRequest;
import dev.zynema.payment.dto.SubscriptionDto;
import dev.zynema.payment.service.SubscriptionCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Billing — Subscriptions", description = "Start a subscription (idempotent)")
public class SubscriptionController {

    /** Header required to make the (billable) operation safe to retry. */
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final SubscriptionCommandService commandService;

    /**
     * Creating a subscription moves money, so it is idempotent by contract:
     * the client must send an {@code Idempotency-Key} and retrying with the
     * same key returns the original response instead of charging twice
     * (ADR-0017). Reusing a key with a different payload is a 409.
     */
    @PostMapping
    @Operation(summary = "Subscribe to a plan",
               description = "Requires the Idempotency-Key header. Replaying a key returns the original response.")
    public ResponseEntity<SubscriptionDto> subscribe(
        @Parameter(description = "Unique per operation; reuse it to retry safely",
                   required = true)
        @RequestHeader(name = IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
        @Valid @RequestBody SubscribeRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        SubscriptionCommandService.IdempotentResponse response =
            commandService.subscribe(idempotencyKey, request, jwt);
        return ResponseEntity.status(HttpStatus.valueOf(response.status())).body(response.body());
    }
}
