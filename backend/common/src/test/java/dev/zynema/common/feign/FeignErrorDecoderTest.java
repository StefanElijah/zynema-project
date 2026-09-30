package dev.zynema.common.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.common.exception.DownstreamServiceException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeignErrorDecoderTest {

    private final FeignErrorDecoder decoder = new FeignErrorDecoder(new ObjectMapper());

    @Test
    @DisplayName("a downstream 5xx becomes a 503 for the original caller")
    void serverErrorBecomesServiceUnavailable() {
        Exception decoded = decoder.decode("UserClient#currentUser()", response(503, null));

        assertThat(decoded).isInstanceOf(DownstreamServiceException.class);
        DownstreamServiceException ex = (DownstreamServiceException) decoded;
        assertThat(ex.resolveStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getServiceName()).isEqualTo("user-service");
    }

    @Test
    @DisplayName("a downstream 4xx keeps its status and message")
    void clientErrorKeepsStatusAndMessage() {
        String body = """
            {"status":409,"error":"Conflict","message":"Profile 'Demo' already exists","path":"/api/v1/users/me/profiles"}
            """;

        DownstreamServiceException ex = (DownstreamServiceException) decoder.decode("UserClient#createProfile()",
            response(409, body));

        assertThat(ex.resolveStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getDownstreamMessage()).isEqualTo("Profile 'Demo' already exists");
        assertThat(ex.getMessage()).contains("user-service").contains("Profile 'Demo' already exists");
    }

    @Test
    @DisplayName("a 404 keeps being a 404")
    void notFoundStaysNotFound() {
        DownstreamServiceException ex = (DownstreamServiceException) decoder.decode("CatalogClient#content()",
            response(404, "{\"status\":404,\"message\":\"Content not found\"}"));

        assertThat(ex.resolveStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("an unparseable body does not break the decoding")
    void unparseableBodyIsTolerated() {
        DownstreamServiceException ex = (DownstreamServiceException) decoder.decode("UserClient#currentUser()",
            response(500, "<html>Internal Server Error</html>"));

        assertThat(ex.getDownstreamMessage()).isNull();
        assertThat(ex.resolveStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("a body-less response is handled")
    void bodylessResponse() {
        DownstreamServiceException ex = (DownstreamServiceException) decoder.decode("UserClient#currentUser()",
            response(502, null));

        assertThat(ex.resolveStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getServiceName()).isEqualTo("user-service");
    }

    private Response response(int status, String body) {
        Request request = Request.create(Request.HttpMethod.GET, "http://user-service/api/v1/users/me",
            Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        Response.Builder builder = Response.builder()
            .status(status)
            .reason("reason")
            .request(request)
            .headers(Map.of());
        if (body != null) {
            builder.body(body.getBytes(StandardCharsets.UTF_8));
        }
        return builder.build();
    }
}
