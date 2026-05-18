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

    @Query(value = """
        SELECT sim.*
        FROM store_inventory_map sim
        JOIN items located_item ON located_item.id = sim.item_id
        LEFT JOIN items requested_item ON requested_item.id = :itemId
        WHERE sim.store_id = :storeId
          AND (
              sim.item_id = :itemId
              OR located_item.catalog_id = :itemId
              OR (
                  requested_item.catalog_id IS NOT NULL
                  AND located_item.catalog_id = requested_item.catalog_id
              )
              OR (
                  requested_item.external_item_id IS NOT NULL
                  AND requested_item.external_item_id <> ''
                  AND located_item.external_item_id = requested_item.external_item_id
              )
              OR f_unaccent(LOWER(located_item.name)) = f_unaccent(LOWER(requested_item.name))
              OR f_unaccent(LOWER(COALESCE(located_item.external_item_id, ''))) = f_unaccent(LOWER(requested_item.name))
              OR EXISTS (
                  SELECT 1
                  FROM p2p_product_catalog catalog
                  WHERE catalog.id = located_item.catalog_id
                    AND (
                        f_unaccent(LOWER(catalog.generic_name)) = f_unaccent(LOWER(requested_item.name))
                        OR f_unaccent(LOWER(catalog.specific_name)) = f_unaccent(LOWER(requested_item.name))
                        OR f_unaccent(LOWER(catalog.generic_name)) LIKE f_unaccent(LOWER(CONCAT('%', requested_item.name, '%')))
                        OR f_unaccent(LOWER(catalog.specific_name)) LIKE f_unaccent(LOWER(CONCAT('%', requested_item.name, '%')))
                    )
              )
          )
        ORDER BY CASE
            WHEN sim.item_id = :itemId THEN 0
            WHEN requested_item.catalog_id IS NOT NULL AND located_item.catalog_id = requested_item.catalog_id THEN 1
            WHEN requested_item.external_item_id IS NOT NULL AND located_item.external_item_id = requested_item.external_item_id THEN 2
            ELSE 3
        END,
        sim.confidence_score DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<StoreInventoryMap> findRoutableByStoreIdAndItemId(@Param("storeId") UUID storeId,
                                                               @Param("itemId") UUID itemId);

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
