package dev.zynema.catalog.repository;

import dev.zynema.catalog.domain.Genre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GenreRepository extends JpaRepository<Genre, UUID> {

    List<Genre> findAllByOrderByNameAsc();

    Optional<Genre> findBySlug(String slug);

    List<Genre> findAllBySlugIn(Collection<String> slugs);
}
