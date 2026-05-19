package com.p2ps.catalog.service;

import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.model.StorePrice;
import com.p2ps.catalog.repository.StorePriceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class StorePriceService {

    private final StorePriceRepository storePriceRepository;
    private final Clock clock;

    @Autowired
    public StorePriceService(StorePriceRepository storePriceRepository) {
        this.storePriceRepository = storePriceRepository;
        this.clock = Clock.systemUTC();
    }

    // 2. Constructorul "secret" pe care îl va folosi doar fișierul de test
    StorePriceService(StorePriceRepository storePriceRepository, Clock clock) {
        this.storePriceRepository = storePriceRepository;
        this.clock = clock;
    }

    @Transactional
    public StorePrice recordStorePrice(ProductCatalog catalogItem, String storeName, BigDecimal price) {
        if (catalogItem == null || catalogItem.getId() == null || isBlank(storeName) || price == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        StorePrice storePrice = storePriceRepository
                .findByCatalogIdAndStoreNameIgnoreCase(catalogItem.getId(), storeName.trim())
                .orElseGet(StorePrice::new);

        storePrice.setCatalogItem(catalogItem);
        storePrice.setStoreName(storeName.trim());
        storePrice.setPrice(price);
        storePrice.setLastUpdatedAt(now);

        return storePriceRepository.save(storePrice);
    }

    @Transactional
    public long deletePricesOlderThanDays(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
        return storePriceRepository.deleteByLastUpdatedAtBefore(cutoff);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
