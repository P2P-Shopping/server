package com.p2ps.catalog.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StorePriceRetentionJobTest {

    @Mock
    private StorePriceService storePriceService;

    private StorePriceRetentionJob storePriceRetentionJob;

    @BeforeEach
    void setUp() {
        storePriceRetentionJob = new StorePriceRetentionJob(storePriceService);
        ReflectionTestUtils.setField(storePriceRetentionJob, "retentionDays", 30);
    }

    @Test
    void purgeExpiredStorePricesShouldDelegateToService() {
        storePriceRetentionJob.purgeExpiredStorePrices();

        verify(storePriceService).deletePricesOlderThanDays(30);
    }
}
