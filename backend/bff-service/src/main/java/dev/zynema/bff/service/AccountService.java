package dev.zynema.bff.service;

import dev.zynema.bff.client.DownstreamErrors;
import dev.zynema.bff.client.PaymentClient;
import dev.zynema.bff.client.UserClient;
import dev.zynema.bff.config.BffCacheConfig;
import dev.zynema.bff.dto.AccountView;
import dev.zynema.bff.dto.WebSection;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The account screen in one round trip.
 *
 * <p>What used to be two frontend calls plus nothing about the plan becomes
 * one composed view: the identity comes from the token itself (no call at
 * all), the local account from user-service (required — without it there is no
 * account screen), and the plan from payment-service (optional: a visitor
 * without a subscription is a paywall, an unavailable payment-service is a
 * warning).
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UpstreamGateway gateway;

    @Cacheable(cacheNames = BffCacheConfig.ACCOUNT, key = "#jwt.subject")
    public Mono<AccountView> account(Jwt jwt) {
        return Mono.zip(
                gateway.currentUser()
                    .transform(publisher -> DownstreamErrors.toApiError("user-service", publisher)),
                gateway.subscription(),
                gateway.entitlements())
            .map(sources -> assemble(identity(jwt), sources.getT1(), sources.getT2(), sources.getT3()));
    }

    private AccountView assemble(AccountView.Identity identity,
                                 UserClient.UserAccount account,
                                 DownstreamResult<PaymentClient.Subscription> subscription,
                                 DownstreamResult<PaymentClient.Entitlements> entitlements) {
        List<WebSection> degraded = new ArrayList<>();
        if (subscription.degraded() || entitlements.degraded()) {
            degraded.add(WebSection.SUBSCRIPTION);
        }
        return new AccountView(identity, toUser(account), toSubscription(subscription.value()),
            toEntitlements(entitlements.value()), degraded);
    }

    /**
     * Claims, not a service call: the token is the source of truth for who is
     * calling, and the realm roles are already used for authorization.
     */
    static AccountView.Identity identity(Jwt jwt) {
        return new AccountView.Identity(
            jwt.getSubject(),
            jwt.getClaimAsString("preferred_username"),
            jwt.getClaimAsString("email"),
            jwt.getClaim("email_verified"),
            jwt.getClaimAsString("name"),
            roles(jwt));
    }

    @SuppressWarnings("unchecked")
    private static List<String> roles(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof List<?> roles) {
            return roles.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private AccountView.User toUser(UserClient.UserAccount account) {
        return new AccountView.User(account.id(), account.email(), account.displayName(),
            account.preferredLanguage(),
            account.profiles() == null ? List.of() : account.profiles().stream()
                .map(profile -> new AccountView.Profile(profile.id(), profile.name(),
                    profile.kids(), profile.language()))
                .toList());
    }

    private AccountView.Subscription toSubscription(PaymentClient.Subscription subscription) {
        if (subscription == null) {
            return null;
        }
        return new AccountView.Subscription(
            subscription.id(),
            subscription.plan() == null ? null : subscription.plan().code(),
            subscription.plan() == null ? null : subscription.plan().name(),
            subscription.status(),
            subscription.currentPeriodEnd(),
            subscription.cancelAtPeriodEnd());
    }

    private AccountView.Entitlements toEntitlements(PaymentClient.Entitlements entitlements) {
        if (entitlements == null) {
            return null;
        }
        return new AccountView.Entitlements(entitlements.active(), entitlements.maxStreams(),
            entitlements.maxQuality(), entitlements.planCode(), entitlements.validUntil());
    }
}
