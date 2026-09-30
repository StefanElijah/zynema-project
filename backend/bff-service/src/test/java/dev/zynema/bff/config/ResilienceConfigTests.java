package dev.zynema.bff.config;

import dev.zynema.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The classification behind every circuit breaker and retry in the BFF.
 *
 * <p>Pure functions, no context: getting these wrong is expensive and invisible
 * (a typo would make a visitor's 404 open the breaker for everybody), so they
 * are pinned here instead of being inferred from a slow integration test.
 */
class ResilienceConfigTests {

    @Test
    @DisplayName("a 4xx is an answer, not a failure")
    void clientErrorsAreNotFailures() {
        assertThat(ResilienceConfig.isNotAFailure(response(HttpStatus.NOT_FOUND))).isTrue();
        assertThat(ResilienceConfig.isNotAFailure(response(HttpStatus.BAD_REQUEST))).isTrue();
        assertThat(ResilienceConfig.isNotAFailure(response(HttpStatus.UNAUTHORIZED))).isTrue();
        assertThat(ResilienceConfig.isNotAFailure(new ResourceNotFoundException("Content", "x"))).isTrue();
    }

    @Test
    @DisplayName("a 5xx or a transport error is a failure")
    void serverAndTransportErrorsAreFailures() {
        assertThat(ResilienceConfig.isNotAFailure(response(HttpStatus.SERVICE_UNAVAILABLE))).isFalse();
        assertThat(ResilienceConfig.isNotAFailure(new IllegalStateException("boom"))).isFalse();
    }

    @Test
    @DisplayName("only 5xx and transport errors are worth retrying")
    void onlyTransientOutcomesAreRetried() {
        assertThat(ResilienceConfig.isTransient(response(HttpStatus.SERVICE_UNAVAILABLE))).isTrue();
        assertThat(ResilienceConfig.isTransient(response(HttpStatus.BAD_GATEWAY))).isTrue();
        assertThat(ResilienceConfig.isTransient(transportFailure())).isTrue();

        assertThat(ResilienceConfig.isTransient(response(HttpStatus.NOT_FOUND))).isFalse();
        assertThat(ResilienceConfig.isTransient(response(HttpStatus.CONFLICT))).isFalse();
        assertThat(ResilienceConfig.isTransient(new ResourceNotFoundException("Content", "x"))).isFalse();
    }

    private static WebClientResponseException response(HttpStatus status) {
        return WebClientResponseException.create(status.value(), status.getReasonPhrase(),
            null, null, null);
    }

    private static WebClientRequestException transportFailure() {
        return new WebClientRequestException(new ConnectException("Connection refused"),
            org.springframework.http.HttpMethod.GET, java.net.URI.create("http://catalog-service/x"),
            org.springframework.http.HttpHeaders.EMPTY);
    }
}
