package dev.zynema.payment;

import dev.zynema.payment.domain.Plan;
import dev.zynema.payment.domain.Subscription;
import dev.zynema.payment.domain.SubscriptionStatus;
import dev.zynema.payment.repository.IdempotencyKeyRepository;
import dev.zynema.payment.repository.PlanRepository;
import dev.zynema.payment.repository.PaymentRepository;
import dev.zynema.payment.repository.SubscriptionRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.github.tomakehurst.wiremock.client.WireMock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Billing API contract: plans, idempotent subscription, cancellation and
 * entitlements — plus what happens when user-service is unhealthy.
 */
@AutoConfigureMockMvc
class SubscriptionApiTests extends AbstractPaymentIntegrationTest {

    private static final String BASIC_PLAN = "81000000-0000-4000-8000-000000000001";
    private static final String PREMIUM_PLAN = "81000000-0000-4000-8000-000000000003";
    private static final String DEMO_SUBJECT = "11111111-1111-4111-8111-111111111111";
    private static final String DEMO_USER = "71000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void reset() {
        paymentRepository.deleteAll();
        subscriptionRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
        circuitBreakerRegistry.circuitBreaker("user-service").reset();
        USER_SERVICE.resetAll();
        stubUserServiceOk();
    }

    // ────────────────────────────── plans ──────────────────────────────

    @Test
    @DisplayName("the pricing catalogue is public")
    void plansArePublic() throws Exception {
        mockMvc.perform(get("/api/v1/payments/plans"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].code", is("basic")))
            .andExpect(jsonPath("$[0].maxStreams", is(1)))
            .andExpect(jsonPath("$[2].code", is("premium")));
    }

    // ─────────────────────────── subscribe ─────────────────────────────

    @Test
    @DisplayName("subscribing requires a token")
    void subscribeRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/payments/subscriptions")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscribeBody(BASIC_PLAN)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("subscribing without an Idempotency-Key is a 400")
    void subscribeRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/payments/subscriptions").with(asDemo())
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscribeBody(BASIC_PLAN)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("subscribing creates the subscription and its payment")
    void subscribeCreatesSubscriptionAndPayment() throws Exception {
        mockMvc.perform(post("/api/v1/payments/subscriptions").with(asDemo())
                .header("Idempotency-Key", "key-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscribeBody(PREMIUM_PLAN)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.plan.code", is("premium")))
            .andExpect(jsonPath("$.status", is("ACTIVE")))
            .andExpect(jsonPath("$.cancelAtPeriodEnd", is(false)));

        assertThat(subscriptionRepository.count()).isEqualTo(1);
        assertThat(paymentRepository.count()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/payments/history").with(asDemo()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].status", is("SUCCEEDED")))
            .andExpect(jsonPath("$[0].planCode", is("premium")));
    }

    @Test
    @DisplayName("retrying with the same key replays the response instead of charging twice")
    void retryingWithSameKeyIsIdempotent() throws Exception {
        String key = "key-retry";
        String body = subscribeBody(BASIC_PLAN);

        String first = mockMvc.perform(post("/api/v1/payments/subscriptions").with(asDemo())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/api/v1/payments/subscriptions").with(asDemo())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(subscriptionRepository.count()).isEqualTo(1);
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("reusing a key with a different payload is a 409")
    void reusingKeyWithDifferentPayloadConflicts() throws Exception {
        mockMvc.perform(post("/api/v1/payments/subscriptions").with(asDemo())
                .header("Idempotency-Key", "key-conflict")
                .contentType(MediaType.APPLICATION_JSON).content(subscribeBody(BASIC_PLAN)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/payments/subscriptions").with(asDemo())
                .header("Idempotency-Key", "key-conflict")
                .contentType(MediaType.APPLICATION_JSON).content(subscribeBody(PREMIUM_PLAN)))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a second subscription for the same account is a 409")
    void secondSubscriptionConflicts() throws Exception {
        mockMvc.perform(post("/api/v1/payments/subscriptions").with(asDemo())
                .header("Idempotency-Key", "key-first")
                .contentType(MediaType.APPLICATION_JSON).content(subscribeBody(BASIC_PLAN)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/payments/subscriptions").with(asDemo())
                .header("Idempotency-Key", "key-second")
                .contentType(MediaType.APPLICATION_JSON).content(subscribeBody(PREMIUM_PLAN)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", is("This account already has an active subscription")));
    }

    @Test
    @DisplayName("an unknown plan is a 404 and does not leave a reserved key behind")
    void unknownPlanIsNotFoundAndReleasesTheKey() throws Exception {
        mockMvc.perform(post("/api/v1/payments/subscriptions").with(asDemo())
                .header("Idempotency-Key", "key-unknown-plan")
                .contentType(MediaType.APPLICATION_JSON).content(subscribeBody(UUID.randomUUID().toString())))
            .andExpect(status().isNotFound());

        assertThat(idempotencyKeyRepository.count()).isZero();
    }

    // ──────────────────── current subscription + cancel ────────────────

    @Test
    @DisplayName("no subscription is a 404, not an empty body")
    void currentSubscriptionWithoutOneIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/payments/subscriptions/me").with(asDemo()))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("cancelling keeps access until the period ends")
    void cancelKeepsAccessUntilPeriodEnd() throws Exception {
        String id = subscribe(BASIC_PLAN, "key-cancel");

        mockMvc.perform(delete("/api/v1/payments/subscriptions/" + id).with(asDemo()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cancelAtPeriodEnd", is(true)))
            .andExpect(jsonPath("$.status", is("ACTIVE")));

        mockMvc.perform(get("/api/v1/payments/subscriptions/me").with(asDemo()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cancelAtPeriodEnd", is(true)));

        mockMvc.perform(delete("/api/v1/payments/subscriptions/" + id).with(asDemo()))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("another account cannot cancel my subscription")
    void cannotCancelSomeoneElsesSubscription() throws Exception {
        // A subscription that belongs to a different local account. The stub
        // always resolves the caller to DEMO_USER, so the ownership check is
        // what has to reject the call.
        Plan plan = planRepository.findById(UUID.fromString(BASIC_PLAN)).orElseThrow();
        Subscription foreign = new Subscription();
        foreign.setUserId(UUID.randomUUID());
        foreign.setPlan(plan);
        foreign.setStatus(SubscriptionStatus.ACTIVE);
        foreign.setCurrentPeriodStart(Instant.now());
        foreign.setCurrentPeriodEnd(Instant.now().plusSeconds(86_400));
        UUID foreignId = subscriptionRepository.save(foreign).getId();

        mockMvc.perform(delete("/api/v1/payments/subscriptions/" + foreignId).with(asDemo()))
            .andExpect(status().isNotFound());
    }

    // ──────────────────────────── entitlements ─────────────────────────

    @Test
    @DisplayName("without a subscription the account gets the free tier")
    void entitlementsWithoutSubscriptionAreFreeTier() throws Exception {
        mockMvc.perform(get("/api/v1/payments/subscriptions/me/entitlements").with(asDemo()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active", is(false)))
            .andExpect(jsonPath("$.maxStreams", is(1)))
            .andExpect(jsonPath("$.maxQuality", is("SD")))
            .andExpect(jsonPath("$.planCode", is("free")));
    }

    @Test
    @DisplayName("entitlements reflect the plan's limits")
    void entitlementsReflectThePlan() throws Exception {
        subscribe(PREMIUM_PLAN, "key-entitlements");

        mockMvc.perform(get("/api/v1/payments/subscriptions/me/entitlements").with(asDemo()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active", is(true)))
            .andExpect(jsonPath("$.maxStreams", is(4)))
            .andExpect(jsonPath("$.maxQuality", is("UHD")))
            .andExpect(jsonPath("$.planCode", is("premium")))
            .andExpect(jsonPath("$.validUntil").isNotEmpty());
    }

    // ────────────────────────── resilience ────────────────────────────

    @Test
    @DisplayName("the account lookup is cached: a second call does not hit user-service")
    void accountResolutionIsCached() throws Exception {
        mockMvc.perform(get("/api/v1/payments/subscriptions/me/entitlements").with(asDemo()))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/payments/subscriptions/me/entitlements").with(asDemo()))
            .andExpect(status().isOk());

        USER_SERVICE.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/api/v1/users/me")));
    }

    @Test
    @DisplayName("the shared Feign interceptor relays the correlation id to the downstream service")
    void correlationIdIsRelayedToDownstream() throws Exception {
        mockMvc.perform(get("/api/v1/payments/subscriptions/me/entitlements").with(asDemo())
                .header("X-Correlation-Id", "corr-relay-test"))
            .andExpect(status().isOk());

        USER_SERVICE.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/api/v1/users/me"))
            .withHeader("X-Correlation-Id", WireMock.equalTo("corr-relay-test")));
    }

    @Test
    @DisplayName("when user-service fails the API answers 503, not 500")
    void userServiceFailureBecomesServiceUnavailable() throws Exception {
        USER_SERVICE.resetAll();
        USER_SERVICE.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/users/me"))
            .willReturn(WireMock.aResponse().withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":500,\"message\":\"boom\"}")));

        mockMvc.perform(get("/api/v1/payments/subscriptions/me").with(asDemo()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status", is(503)));
    }

    @Test
    @DisplayName("repeated failures open the circuit and it fails fast")
    void circuitBreakerOpensAfterRepeatedFailures() throws Exception {
        USER_SERVICE.resetAll();
        USER_SERVICE.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/users/me"))
            .willReturn(WireMock.aResponse().withStatus(503)));

        for (int i = 0; i < 12; i++) {
            // Use a different subject each time so the account cache never masks
            // the down dependency.
            mockMvc.perform(get("/api/v1/payments/subscriptions/me").with(asSubject("subject-" + i)))
                .andExpect(status().isServiceUnavailable());
        }

        assertThat(circuitBreakerRegistry.circuitBreaker("user-service").getState())
            .isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);

        // With the circuit open it fails fast instead of waiting for the timeout.
        mockMvc.perform(get("/api/v1/payments/subscriptions/me").with(asSubject("subject-final")))
            .andExpect(status().isServiceUnavailable());
    }

    // ────────────────────────────── helpers ───────────────────────────

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asDemo() {
        return asSubject(DEMO_SUBJECT);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asSubject(String subject) {
        return jwt().jwt(jwt -> jwt.subject(subject).claim("email", subject + "@zynema.dev"))
            .authorities(new SimpleGrantedAuthority("ROLE_user"));
    }

    private String subscribe(String planId, String key) throws Exception {
        String body = mockMvc.perform(post("/api/v1/payments/subscriptions").with(asDemo())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(subscribeBody(planId)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private String subscribeBody(String planId) {
        return """
            {"planId": "%s", "paymentMethod": "simulated_card"}
            """.formatted(planId);
    }

    /**
     * The Authorization relay is covered by the unit test of the interceptor;
     * here the stub stays header-agnostic so the API tests are about behaviour.
     */
    private void stubUserServiceOk() {
        USER_SERVICE.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/users/me"))
            .willReturn(WireMock.aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"id": "%s", "email": "demo@zynema.dev", "displayName": "Demo User"}
                    """.formatted(DEMO_USER))));
    }
}
