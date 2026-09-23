package dev.zynema.user.repository;

import dev.zynema.user.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = {"profiles"})
    Optional<User> findWithProfilesById(UUID id);

    @EntityGraph(attributePaths = {"profiles"})
    Optional<User> findWithProfilesByEmail(String email);
}
