package dev.zynema.bff.service;

import dev.zynema.bff.client.CatalogClient;
import dev.zynema.bff.dto.FranchiseCard;
import dev.zynema.bff.dto.FranchiseView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/** Maps catalog saga payloads to the cards and the saga detail screen. */
@Service
@RequiredArgsConstructor
public class FranchiseService {

    private final UpstreamGateway gateway;

    public Mono<List<FranchiseCard>> list() {
        return gateway.franchises().map(franchises -> franchises.stream()
            .map(FranchiseService::card)
            .toList());
    }

    public Mono<FranchiseView> detail(String slug) {
        return gateway.franchise(slug).map(detail -> new FranchiseView(
            detail.id(),
            detail.slug(),
            detail.name(),
            detail.description(),
            detail.coverUrl(),
            detail.items() == null ? List.of() : detail.items().stream().map(ViewMapper::card).toList()));
    }

    private static FranchiseCard card(CatalogClient.FranchiseSummary summary) {
        return new FranchiseCard(
            summary.id(),
            summary.slug(),
            summary.name(),
            summary.description(),
            summary.coverUrl(),
            summary.itemCount());
    }
}
