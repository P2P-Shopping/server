package com.p2ps.catalog.repository;

import com.p2ps.catalog.model.ProductCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductCatalogRepository extends JpaRepository<ProductCatalog, UUID> {

    Optional<ProductCatalog> findBySpecificNameAndBrand(String specificName, String brand);

    List<ProductCatalog> findTop50ByOrderByPurchaseCountDesc();
    
    @Query(value = """
            SELECT sim.store_id 
            FROM store_inventory_map sim 
            JOIN items i ON sim.item_id = i.id 
            WHERE i.catalog_id = :catalogId 
            GROUP BY sim.store_id 
            ORDER BY MAX(sim.confidence_score) DESC
            """, nativeQuery = true)
    List<UUID> findBestStoresForCatalogProduct(UUID catalogId);
}