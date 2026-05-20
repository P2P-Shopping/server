package com.p2ps.catalog.repository;

import com.p2ps.catalog.model.StorePrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StorePriceRepository extends JpaRepository<StorePrice, UUID> {

    @Query("""
            select sp
            from StorePrice sp
            where sp.catalogItem.id = :catalogId
              and lower(sp.storeName) = lower(:storeName)
            """)
    Optional<StorePrice> findByCatalogIdAndStoreNameIgnoreCase(@Param("catalogId") UUID catalogId,
                                                               @Param("storeName") String storeName);

    @Modifying
    long deleteByLastUpdatedAtBefore(LocalDateTime cutoff);
}
