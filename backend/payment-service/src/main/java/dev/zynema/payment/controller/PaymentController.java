package dev.zynema.payment.controller;

import dev.zynema.payment.dto.EntitlementsDto;
import dev.zynema.payment.dto.PaymentDto;
import dev.zynema.payment.dto.PlanDto;
import dev.zynema.payment.dto.SubscriptionDto;
import dev.zynema.payment.service.PlanQueryService;
import dev.zynema.payment.service.SubscriptionCommandService;
import dev.zynema.payment.service.SubscriptionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Validated
@Tag(name = "Billing", description = "Plans, subscription and payment history")
public class PaymentController {

    private final PlanQueryService planQueryService;
    private final SubscriptionQueryService subscriptionQueryService;
    private final SubscriptionCommandService subscriptionCommandService;

    @GetMapping("/plans")
    @Operation(summary = "List active plans", description = "Public pricing catalogue, cached.")
    public List<PlanDto> listPlans() {
        return planQueryService.listActivePlans();
    }

    @GetMapping("/subscriptions/me")
    @Operation(summary = "My current subscription")
    public SubscriptionDto currentSubscription(@AuthenticationPrincipal Jwt jwt) {
        return subscriptionQueryService.currentSubscription(jwt);
    }

    @GetMapping("/subscriptions/me/history")
    @Operation(summary = "My subscription history, newest first")
    public List<SubscriptionDto> subscriptionHistory(@AuthenticationPrincipal Jwt jwt) {
        return subscriptionQueryService.subscriptionHistory(jwt);
    }

    @GetMapping("/subscriptions/me/entitlements")
    @Operation(summary = "What the account may do right now",
               description = "Consumed by playback-service to enforce concurrent streams. "
                   + "Without a subscription it answers the free tier.")
    public EntitlementsDto entitlements(@AuthenticationPrincipal Jwt jwt) {
        return subscriptionQueryService.entitlements(jwt);
    }

    @DeleteMapping("/subscriptions/{subscriptionId}")
    @Operation(summary = "Cancel my subscription",
               description = "Keeps access until the paid period ends (cancel at period end).")
    public SubscriptionDto cancelSubscription(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID subscriptionId) {
        return subscriptionCommandService.cancel(subscriptionId, jwt);
    }

    @GetMapping("/history")
    @Operation(summary = "My payment history, newest first")
    public List<PaymentDto> paymentHistory(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return subscriptionQueryService.paymentHistory(jwt, limit);
    }
}
