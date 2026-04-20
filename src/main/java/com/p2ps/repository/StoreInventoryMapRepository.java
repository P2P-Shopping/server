package com.p2ps.repository;

import com.p2ps.entity.StoreInventoryMap;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface StoreInventoryMapRepository extends JpaRepository<StoreInventoryMap, UUID> {
    Optional<StoreInventoryMap> findByStoreIdAndItemId(UUID storeId, UUID itemId);
import com.p2ps.model.StoreInventoryMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface StoreInventoryMapRepository extends JpaRepository<StoreInventoryMap, UUID> {

    // Acest query va scădea scorul cu o valoare anume pentru toate produsele nemișcate de ceva timp
    @Modifying(clearAutomatically = true, flushAutomatically = true)    @Transactional
    @Query("""
        UPDATE StoreInventoryMap s
        SET s.confidenceScore =
            CASE
                WHEN s.confidenceScore - :penalty < 0 THEN 0
                ELSE s.confidenceScore - :penalty
            END
        WHERE s.lastUpdated < :cutoffDate
          AND s.confidenceScore > 0
    """)
    int applyDecayToOldRecords(@Param("penalty") Double penalty, @Param("cutoffDate") LocalDateTime cutoffDate);
}