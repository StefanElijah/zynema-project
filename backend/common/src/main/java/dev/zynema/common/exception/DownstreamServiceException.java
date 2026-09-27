package dev.zynema.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Raised when a call to another service fails.
 *
 * <p>Carries the HTTP status the downstream service answered with, when there
 * was one. The global exception handlers use it to keep the semantics intact:
 * a 409 from the dependency stays a 409 for the original caller, while an
 * unreachable or failing dependency becomes a 503 (the problem is upstream,
 * not in this service).
 *
 * <p>Without this distinction every inter-service failure would surface as a
 * generic 500, which tells the client "we are broken" when the truth is
 * "that dependency is down" — and makes retry logic on the client impossible.
 */
@Getter
public class DownstreamServiceException extends RuntimeException {

    private final String serviceName;
    private final HttpStatus downstreamStatus;
    private final String downstreamMessage;

    public DownstreamServiceException(String serviceName, String message) {
        this(serviceName, message, null, null);
    }

    public DownstreamServiceException(String serviceName, String message,
                                      HttpStatus downstreamStatus, String downstreamMessage) {
        super(message);
        this.serviceName = serviceName;
        this.downstreamStatus = downstreamStatus;
        this.downstreamMessage = downstreamMessage;
    }

    /** Status to return to the original caller: the downstream 4xx, else 503. */
    public HttpStatus resolveStatus() {
        if (downstreamStatus != null && downstreamStatus.is4xxClientError()) {
            return downstreamStatus;
        }
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
