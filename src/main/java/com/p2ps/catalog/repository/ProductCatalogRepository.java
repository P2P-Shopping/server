package com.p2ps.catalog.repository;

import com.p2ps.catalog.model.ProductCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductCatalogRepository extends JpaRepository<ProductCatalog, UUID> {

    @Query("SELECT p FROM ProductCatalog p WHERE p.specificName = :specificName AND ((p.brand IS NULL AND :brand IS NULL) OR p.brand = :brand)")
    Optional<ProductCatalog> findBySpecificNameAndBrand(@Param("specificName") String specificName, @Param("brand") String brand);

    List<ProductCatalog> findTop50ByOrderByPurchaseCountDesc();
    
    @Query(value = """
            SELECT sim.store_id 
            FROM store_inventory_map sim 
            JOIN items i ON sim.item_id = i.id 
            WHERE i.catalog_id = :catalogId 
            GROUP BY sim.store_id 
            ORDER BY AVG(sim.confidence_score) DESC
            LIMIT 10
            """, nativeQuery = true)
    List<UUID> findBestStoresForCatalogProduct(@Param("catalogId") UUID catalogId);
}