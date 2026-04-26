package com.p2ps.repository;

import com.p2ps.model.StoreInventoryMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoreInventoryMapRepository extends JpaRepository<StoreInventoryMap, UUID> {

    Optional<StoreInventoryMap> findByStoreIdAndItemId(UUID storeId, UUID itemId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        UPDATE StoreInventoryMap s
        SET s.confidenceScore =
            CASE
                WHEN s.confidenceScore - :penalty < :minConfidenceFloor THEN :minConfidenceFloor
                ELSE s.confidenceScore - :penalty
            END
        WHERE s.lastUpdated < :cutoffDate
          AND s.confidenceScore > :minConfidenceFloor
    """)
    int applyDecayToOldRecords(@Param("penalty") Double penalty,
                               @Param("cutoffDate") LocalDateTime cutoffDate,
                               @Param("minConfidenceFloor") Double minConfidenceFloor);
}
