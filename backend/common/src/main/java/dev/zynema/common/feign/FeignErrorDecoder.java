package dev.zynema.common.feign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.common.exception.DownstreamServiceException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * Turns a failing HTTP response from another service into a
 * {@link DownstreamServiceException} that carries the original status.
 *
 * <p>It also unwraps the downstream {@code ApiError} envelope, so the caller
 * sees the real reason ("email already registered") instead of a generic
 * "Feign Client Error".
 *
 * <p>The service name is taken from the request URL (which is the service id
 * when the client uses {@code lb://}), so a single bean serves every client.
 */
@Slf4j
@RequiredArgsConstructor
public class FeignErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    @Override
    public Exception decode(String methodKey, Response response) {
        HttpStatus status = HttpStatus.resolve(response.status());
        String service = resolveServiceName(response);
        String downstreamMessage = extractMessage(response);

        String message = "Call to %s (%s) failed with status %d%s".formatted(
            service, methodKey, response.status(),
            downstreamMessage == null ? "" : ": " + downstreamMessage);

        if (status != null && status.is5xxServerError()) {
            log.error("Downstream call failed: {}", message);
        } else {
            log.warn("Downstream call rejected: {}", message);
        }

        return new DownstreamServiceException(service, message, status, downstreamMessage);
    }

    private String resolveServiceName(Response response) {
        try {
            String host = URI.create(response.request().url()).getHost();
            return StringUtils.hasText(host) ? host : "downstream service";
        } catch (RuntimeException ex) {
            return "downstream service";
        }
    }

    /** Reads the {@code message} field of the shared ApiError envelope, if present. */
    private String extractMessage(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream body = response.body().asInputStream()) {
            JsonNode error = objectMapper.readTree(body);
            JsonNode message = error.get("message");
            return message == null || message.isNull() ? null : message.asText();
        } catch (IOException | RuntimeException ex) {
            log.debug("Could not parse the downstream error body", ex);
            return null;
        }
    }
}
