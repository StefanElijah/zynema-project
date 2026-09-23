package dev.zynema.catalog.repository;

import dev.zynema.catalog.domain.Credit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CreditRepository extends JpaRepository<Credit, UUID> {

    @EntityGraph(attributePaths = {"person"})
    List<Credit> findByContentIdOrderByBillingOrderAsc(UUID contentId);
}
