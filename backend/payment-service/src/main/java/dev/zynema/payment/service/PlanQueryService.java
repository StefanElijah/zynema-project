package dev.zynema.payment.service;

import dev.zynema.payment.config.CacheConfig;
import dev.zynema.payment.dto.PlanDto;
import dev.zynema.payment.mapper.PaymentMapper;
import dev.zynema.payment.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Pricing catalogue. Read-only and cached: it changes rarely. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanQueryService {

    private final PlanRepository planRepository;
    private final PaymentMapper mapper;

    @Cacheable(cacheNames = CacheConfig.PLANS, key = "'active'")
    public List<PlanDto> listActivePlans() {
        return mapper.toPlanDtoList(planRepository.findByActiveTrueOrderByPriceAsc());
    }
}
