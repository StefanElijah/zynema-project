package dev.zynema.payment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * user-service, reached through Eureka and called with the end user's token
 * (relayed by the shared {@code FeignRequestInterceptor}), which is what makes
 * {@code /users/me} valid: the downstream service applies its own rules to the
 * same identity.
 *
 * <p>The name matches the Eureka service id, which also makes it the key for
 * both the Feign client configuration ({@code spring.cloud.openfeign.client
 * .config.user-service.*}) and the resilience4j instances. No {@code contextId}
 * is set on purpose: adding one would rename the configuration key and silently
 * disconnect the timeouts and circuit breaker from this client.
 */
@FeignClient(name = "user-service", path = "/api/v1/users")
public interface UserServiceClient {

    /** Resolves the local account of the authenticated caller. */
    @GetMapping("/me")
    UserAccountDto currentUser();
}
