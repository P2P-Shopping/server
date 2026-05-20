package com.p2ps.catalog.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GlobalCatalogPopulationJobTest {

    @Mock
    private GlobalCatalogPopulationService globalCatalogPopulationService;

    private GlobalCatalogPopulationJob globalCatalogPopulationJob;

    @BeforeEach
    void setUp() {
        globalCatalogPopulationJob = new GlobalCatalogPopulationJob(globalCatalogPopulationService);
        ReflectionTestUtils.setField(globalCatalogPopulationJob, "minDistinctUsers", 3);
    }

    @Test
    void populateGlobalCatalogShouldDelegateToService() {
        globalCatalogPopulationJob.populateGlobalCatalog();

        verify(globalCatalogPopulationService).populateFromPopularUnknownProducts(3);
    }
}
