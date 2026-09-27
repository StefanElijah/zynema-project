package dev.zynema.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.common.exception.BusinessRuleException;
import dev.zynema.common.exception.ConflictException;
import dev.zynema.payment.domain.IdempotencyKey;
import dev.zynema.payment.domain.IdempotencyStatus;
import dev.zynema.payment.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Idempotent request handling (ADR-0017).
 *
 * <p>The key is <strong>reserved before</strong> the business logic runs, in its
 * own committed transaction. A concurrent duplicate therefore finds the
 * reservation instead of executing the charge again — a lookup-then-execute
 * design cannot prevent that.
 *
 * <p>Reusing a key with a different payload is a client bug and answers 409
 * rather than silently returning the previous response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Duration RETENTION = Duration.ofHours(24);
    private static final int MAX_KEY_LENGTH = 255;

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Result of a reservation attempt.
     *
     * @param replay   a completed response that must be returned verbatim
     * @param reserved true when this caller is the one that must execute the work
     */
    public record Reservation(Optional<CompletedResponse> replay, boolean reserved) {
    }

    public record CompletedResponse(int status, Map<String, Object> body) {
    }

    /** Hashes the request payload so a key reused with a different body is detected. */
    public String hash(Object payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(objectMapper.writeValueAsBytes(payload));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | JsonProcessingException ex) {
            throw new IllegalStateException("Could not hash the request payload", ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(String key, String requestHash) {
        validate(key);
        Optional<IdempotencyKey> existing = repository.findById(key);
        if (existing.isPresent()) {
            return new Reservation(existingResponse(existing.get(), requestHash), false);
        }
        try {
            IdempotencyKey reservation = new IdempotencyKey();
            reservation.setKey(key);
            reservation.setRequestHash(requestHash);
            reservation.setStatus(IdempotencyStatus.IN_PROGRESS);
            reservation.setExpiresAt(Instant.now().plus(RETENTION));
            repository.saveAndFlush(reservation);
            return new Reservation(Optional.empty(), true);
        } catch (DataIntegrityViolationException ex) {
            // Another request reserved the same key between the lookup and the
            // insert: read its state instead of running the work twice.
            log.info("Idempotency key '{}' was reserved concurrently", key);
            IdempotencyKey concurrent = repository.findById(key)
                .orElseThrow(() -> ex);
            return new Reservation(existingResponse(concurrent, requestHash), false);
        }
    }

    /**
     * Releases a reservation whose work failed. The attempt had no effect, so
     * the client may retry with the same key instead of being told the request
     * is "still being processed" forever.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String key) {
        repository.findById(key)
            .filter(entry -> entry.getStatus() == IdempotencyStatus.IN_PROGRESS)
            .ifPresent(repository::delete);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String key, int responseCode, Object responseBody) {
        IdempotencyKey entry = repository.findById(key).orElseThrow();
        entry.setStatus(IdempotencyStatus.COMPLETED);
        entry.setResponseCode(responseCode);
        entry.setResponseBody(objectMapper.convertValue(responseBody, new TypeReference<>() {
        }));
        repository.save(entry);
    }

    private Optional<CompletedResponse> existingResponse(IdempotencyKey entry, String requestHash) {
        if (!entry.getRequestHash().equals(requestHash)) {
            throw new ConflictException(
                "Idempotency-Key '%s' was already used with a different request payload".formatted(entry.getKey()));
        }
        if (entry.getStatus() != IdempotencyStatus.COMPLETED) {
            throw new ConflictException(
                "A request with Idempotency-Key '%s' is still being processed".formatted(entry.getKey()));
        }
        return Optional.of(new CompletedResponse(entry.getResponseCode(), entry.getResponseBody()));
    }

    private void validate(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessRuleException("The Idempotency-Key header is required");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new BusinessRuleException("Idempotency-Key must be at most %d characters".formatted(MAX_KEY_LENGTH));
        }
    }


}
