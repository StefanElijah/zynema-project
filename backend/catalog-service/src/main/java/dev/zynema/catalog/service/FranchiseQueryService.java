package dev.zynema.catalog.service;

import dev.zynema.catalog.dto.FranchiseDetailDto;
import dev.zynema.catalog.dto.FranchiseSummaryDto;
import dev.zynema.catalog.repository.FranchiseReadRepository;
import dev.zynema.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read side of the saga grouping. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FranchiseQueryService {

    private final FranchiseReadRepository repository;

    public List<FranchiseSummaryDto> list() {
        return repository.findAll();
    }

    public FranchiseDetailDto get(String slug) {
        return repository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Franchise", slug));
    }
}
