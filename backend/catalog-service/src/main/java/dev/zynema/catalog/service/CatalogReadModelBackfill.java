package dev.zynema.catalog.service;

import dev.zynema.catalog.repository.CatalogReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills an empty read model from the write tables at startup.
 *
 * <p>Needed exactly once per database: the migration creates the projection
 * empty, and the write tables already hold the seeded catalogue. Running it
 * only when the projection is empty keeps it harmless everywhere else — a
 * populated model is rebuilt by the projector on every write, and re-projecting
 * on every boot would turn a restart into a full scan.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogReadModelBackfill implements ApplicationRunner {

    private final CatalogReadRepository readRepository;
    private final CatalogProjector projector;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long existing = readRepository.count();
        if (existing > 0) {
            log.debug("Read model already holds {} titles; nothing to backfill", existing);
            return;
        }
        int projected = projector.rebuildAll();
        log.info("Read model was empty: projected {} catalogue titles from the write tables", projected);
    }
}
