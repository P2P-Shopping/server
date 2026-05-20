package com.p2ps.catalog.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GlobalCatalogPopulationJob {

    private final GlobalCatalogPopulationService globalCatalogPopulationService;

    @Value("${catalog.population.min-distinct-users:3}")
    private int minDistinctUsers;

    public GlobalCatalogPopulationJob(GlobalCatalogPopulationService globalCatalogPopulationService) {
        this.globalCatalogPopulationService = globalCatalogPopulationService;
    }

    @Scheduled(fixedRate = 2*60000)
    public void populateGlobalCatalog() {
        int processedCount = globalCatalogPopulationService.populateFromPopularUnknownProducts(minDistinctUsers);
        log.info("[GLOBAL_CATALOG_POPULATION] Processed {} popular unknown product groups", processedCount);
    }
}
