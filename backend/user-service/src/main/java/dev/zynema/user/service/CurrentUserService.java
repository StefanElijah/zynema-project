package dev.zynema.user.service;

import dev.zynema.common.messaging.OutboxRecorder;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.UserEvent;
import dev.zynema.user.domain.User;
import dev.zynema.user.dto.UserDto;
import dev.zynema.user.mapper.UserMapper;
import dev.zynema.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Resolves the local account from the validated JWT.
 *
 * <p>Just-in-time provisioning: the first authenticated call creates the local
 * row (or links an existing one by verified email) from the token claims. This
 * keeps user-service as the owner of account data while Keycloak stays the
 * source of identity, and it means an account can be pre-created by an admin
 * without ever diverging from the IdP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final OutboxRecorder outbox;

    @Transactional
    public UserDto resolve(Jwt jwt) {
        return userRepository.findWithProfilesByKeycloakSubject(jwt.getSubject())
            .map(userMapper::toDto)
            .orElseGet(() -> userMapper.toDto(provision(jwt)));
    }

    /**
     * Local id of the caller, used by the self-service endpoints to delegate to
     * the same services the admin API uses.
     */
    @Transactional
    public UUID resolveId(Jwt jwt) {
        return userRepository.findByKeycloakSubject(jwt.getSubject())
            .map(User::getId)
            .orElseGet(() -> provision(jwt).getId());
    }

    private User provision(Jwt jwt) {
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        boolean emailVerified = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));

        // An account may already exist because an admin created it, because the
        // seed created it, or because the user signed up with another provider.
        // Linking is only allowed when the IdP vouches for the email address.
        if (emailVerified && email != null) {
            User existing = userRepository.findByEmail(email).orElse(null);
            if (existing != null) {
                log.info("Linking account {} to Keycloak subject {}", existing.getId(), subject);
                existing.setKeycloakSubject(subject);
                return publishRegistration(userRepository.save(existing));
            }
        }

        User user = new User();
        user.setKeycloakSubject(subject);
        user.setEmail(email != null && !email.isBlank() ? email : subject + "@keycloak.local");
        user.setDisplayName(displayName(jwt));
        user.setPreferredLanguage(preferredLanguage(jwt));
        log.info("Provisioned local account for Keycloak subject {}", subject);
        // Concurrent first requests would both try to insert; the unique
        // constraints (keycloak_subject, email) keep the data correct and the
        // loser of the race gets a conflict the client can retry.
        return publishRegistration(userRepository.save(user));
    }

    /**
     * The account becoming known is a fact other services act on: the event is
     * appended in the same transaction as the row (ADR-0026), which makes it
     * the only place the email is published — commands never carry it.
     */
    private User publishRegistration(User user) {
        outbox.append(KafkaTopics.USER_EVENTS, user.getId().toString(),
            new UserEvent.UserRegistered(user.getId(), user.getEmail(), user.getDisplayName(), Instant.now()));
        return user;
    }

    private String displayName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        String username = jwt.getClaimAsString("preferred_username");
        if (username != null && !username.isBlank()) {
            return username;
        }
        return jwt.getClaimAsString("email");
    }

    private String preferredLanguage(Jwt jwt) {
        String locale = jwt.getClaimAsString("locale");
        if (locale != null && locale.length() >= 2) {
            return locale.substring(0, 2).toLowerCase();
        }
        return "es";
    }
}
