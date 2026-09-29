package dev.zynema.bff.controller;

import dev.zynema.bff.client.DownstreamErrors;
import dev.zynema.bff.client.PaymentClient;
import dev.zynema.bff.service.UpstreamGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * The pricing page and checkout.
 *
 * <p>The plans are the shop window and need no token. The subscription is a
 * write, and it is <strong>idempotent by contract</strong>: the SPA generates
 * the {@code Idempotency-Key} and the BFF forwards it untouched, so a retried
 * checkout — a double click, a timeout, a proxy replay — produces one
 * subscription and not two. That is why the key is part of the endpoint's
 * signature rather than a header quietly invented here.
 *
 * <p>The upstream status is preserved ({@code 201} created, {@code 200} when the
 * key had already been used), because the client is entitled to know whether
 * this call is what created the subscription.
 */
@RestController
@RequestMapping("/api/v1/web")
@RequiredArgsConstructor
@Tag(name = "Web BFF", description = "Aggregated views for the SPA")
public class PaymentWebController {

    /** Same header payment-service documents; forwarded verbatim. */
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final UpstreamGateway gateway;

    @GetMapping("/plans")
    @Operation(summary = "Active plans, cheapest first")
    public Mono<List<PaymentClient.Plan>> plans() {
        return gateway.plans()
            .transform(publisher -> DownstreamErrors.toApiError("payment-service", publisher));
    }

    @PostMapping("/subscriptions")
    @Operation(summary = "Subscribe the account to a plan",
        description = "Requires an Idempotency-Key; 409 when the account already has an active subscription")
    public Mono<ResponseEntity<PaymentClient.Subscription>> subscribe(
        @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
        @Valid @RequestBody SubscribeRequest request) {
        return gateway.subscribe(idempotencyKey, request.planId(), request.paymentMethod())
            .transform(publisher -> DownstreamErrors.toApiError("payment-service", publisher));
    }

    /** The checkout form as the SPA sends it. */
    public record SubscribeRequest(
        @NotNull UUID planId,
        @Size(max = 40) String paymentMethod
    ) {
    }
}
