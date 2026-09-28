package dev.zynema.bff.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.common.exception.DownstreamServiceException;
import dev.zynema.common.exception.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Turns transport failures into the platform's error contract.
 *
 * <p>Runs <strong>outside</strong> the resilience aspects on purpose: retry and
 * the circuit breaker must classify the real HTTP outcome (a 404 is not a
 * failure, a connection refusal is), so the mapping happens once the call has
 * definitively given up. A mapped error keeps the downstream 4xx status and the
 * downstream message; anything else becomes a 503 — "that dependency is down",
 * not "the BFF is broken".
 */
public final class DownstreamErrors {

    private static final ObjectMapper JSON = new ObjectMapper();

    private DownstreamErrors() {
    }

    public static <T> Mono<T> toApiError(String service, Mono<T> publisher) {
        return publisher.onErrorResume(ex -> Mono.error(convert(service, ex)));
    }

    static Throwable convert(String service, Throwable ex) {
        if (ex instanceof DownstreamServiceException
            || ex instanceof ResourceNotFoundException
            || ex instanceof CallNotPermittedException) {
            return ex;
        }
        if (ex instanceof WebClientResponseException response) {
            HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
            String message = downstreamMessage(response.getResponseBodyAsString());
            return new DownstreamServiceException(service,
                "%s responded %d".formatted(service, response.getStatusCode().value()),
                status, message);
        }
        String detail = ex instanceof WebClientRequestException ? "timed out or refused the connection" : ex.getMessage();
        return new DownstreamServiceException(service, "Could not reach %s: %s".formatted(service, detail));
    }

    /**
     * The envelope carries the message the downstream wants the client to see;
     * falling back to the status line keeps the original error observable.
     */
    private static String downstreamMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode message = JSON.readTree(body).get("message");
            return message == null || message.isNull() ? body : message.asText();
        } catch (Exception ignored) {
            return body;
        }
    }
}
