package com.p2ps.catalog.service;

import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.model.StorePrice;
import com.p2ps.catalog.repository.StorePriceRepository;
import com.p2ps.store.model.StoreGeofence;
import com.p2ps.store.repository.StoreGeofenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class StorePriceService {

    private final StorePriceRepository storePriceRepository;
    private final StoreGeofenceRepository storeGeofenceRepository;
    private final Clock clock;

    @Autowired
    public StorePriceService(StorePriceRepository storePriceRepository,
                             StoreGeofenceRepository storeGeofenceRepository) {
        this.storePriceRepository = storePriceRepository;
        this.storeGeofenceRepository = storeGeofenceRepository;
        this.clock = Clock.systemUTC();
    }

    StorePriceService(StorePriceRepository storePriceRepository,
                      StoreGeofenceRepository storeGeofenceRepository,
                      Clock clock) {
        this.storePriceRepository = storePriceRepository;
        this.storeGeofenceRepository = storeGeofenceRepository;
        this.clock = clock;
    }

    @Transactional
    public StorePrice recordStorePrice(ProductCatalog catalogItem, UUID storeId, BigDecimal price) {
        if (catalogItem == null || catalogItem.getId() == null || storeId == null || price == null
                || price.compareTo(BigDecimal.ZERO) < 0) {
            return null;
        }

        StoreGeofence store = storeGeofenceRepository.findById(storeId).orElse(null);
        if (store == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        StorePrice storePrice = storePriceRepository
                .findByCatalogIdAndStoreId(catalogItem.getId(), storeId)
                .orElseGet(StorePrice::new);

        storePrice.setCatalogItem(catalogItem);
        storePrice.setStore(store);
        storePrice.setPrice(price);
        storePrice.setLastUpdatedAt(now);

        return storePriceRepository.save(storePrice);
    }

    @Transactional
    public StorePrice recordStorePrice(ProductCatalog catalogItem, String ignoredStoreName, BigDecimal price) {
        return recordStorePrice(catalogItem, (UUID) null, price);
    }

    @Transactional
    public long deletePricesOlderThanDays(int retentionDays) {
        if (retentionDays < 0) {
            throw new IllegalArgumentException("retentionDays must be zero or positive");
        }
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
        return storePriceRepository.deleteByLastUpdatedAtBefore(cutoff);
    }

}
