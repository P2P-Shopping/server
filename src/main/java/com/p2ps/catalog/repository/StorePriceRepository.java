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
              and sp.store.id = :storeId
            """)
    Optional<StorePrice> findByCatalogIdAndStoreId(@Param("catalogId") UUID catalogId,
                                                   @Param("storeId") UUID storeId);

    @Modifying
    long deleteByLastUpdatedAtBefore(LocalDateTime cutoff);
}
