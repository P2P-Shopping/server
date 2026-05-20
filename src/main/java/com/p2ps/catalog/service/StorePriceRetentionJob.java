package com.p2ps.catalog.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StorePriceRetentionJob {

    private final StorePriceService storePriceService;

    @Value("${catalog.store-price.retention-days:30}")
    private int retentionDays;

    public StorePriceRetentionJob(StorePriceService storePriceService) {
        this.storePriceService = storePriceService;
    }

    @Scheduled(cron = "${catalog.store-price.retention-cron:0 0 2 * * *}", zone = "${catalog.store-price.retention-zone:UTC}")
    public void purgeExpiredStorePrices() {
        long deletedCount = storePriceService.deletePricesOlderThanDays(retentionDays);
        log.info("[STORE_PRICE_RETENTION] Deleted {} store price records older than {} days", deletedCount, retentionDays);
    }
}
