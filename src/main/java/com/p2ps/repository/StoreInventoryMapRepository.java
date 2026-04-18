package com.p2ps.repository;

import com.p2ps.entity.StoreInventoryMap;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface StoreInventoryMapRepository extends JpaRepository<StoreInventoryMap, UUID> {
    Optional<StoreInventoryMap> findByStoreIdAndItemId(UUID storeId, UUID itemId);
}