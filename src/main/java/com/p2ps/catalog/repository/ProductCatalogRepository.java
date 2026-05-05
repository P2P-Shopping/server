package com.p2ps.catalog.repository;

import com.p2ps.catalog.model.ProductCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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

    @Modifying
    @Query(value = """
        INSERT INTO p2p_product_catalog (id, generic_name, specific_name, brand, category, estimated_price, purchase_count)
        VALUES (gen_random_uuid(), :genericName, :specificName, :brand, :category, :price, 1)
        ON CONFLICT (specific_name, COALESCE(brand, ''))
        DO UPDATE SET
            purchase_count = p2p_product_catalog.purchase_count + 1,
            estimated_price = COALESCE(:price, p2p_product_catalog.estimated_price),
            category = COALESCE(:category, p2p_product_catalog.category)
        """, nativeQuery = true)
    void upsertProduct(@Param("genericName") String genericName,
                       @Param("specificName") String specificName,
                       @Param("brand") String brand,
                       @Param("category") String category,
                       @Param("price") BigDecimal price);

    // Transformata in Native Query pentru a suporta unaccent()
    @Query(value = """
            SELECT p.*
            FROM p2p_product_catalog p
            WHERE unaccent(LOWER(p.generic_name)) LIKE unaccent(LOWER(CONCAT('%', :keyword, '%')))
               OR unaccent(LOWER(p.specific_name)) LIKE unaccent(LOWER(CONCAT('%', :keyword, '%')))
               OR unaccent(LOWER(COALESCE(p.brand, ''))) LIKE unaccent(LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY p.purchase_count DESC
            """, nativeQuery = true)
    List<ProductCatalog> searchByKeyword(@Param("keyword") String keyword);

    // Adaugat unaccent() peste tot pentru fuzzy search
    @Query(value = """
            SELECT p.*
            FROM p2p_product_catalog p
            WHERE unaccent(LOWER(p.generic_name)) LIKE unaccent(LOWER(CONCAT('%', :keyword, '%')))
               OR unaccent(LOWER(p.specific_name)) LIKE unaccent(LOWER(CONCAT('%', :keyword, '%')))
               OR unaccent(LOWER(COALESCE(p.brand, ''))) LIKE unaccent(LOWER(CONCAT('%', :keyword, '%')))
               OR similarity(unaccent(LOWER(p.generic_name)), unaccent(LOWER(:keyword))) > 0.4
               OR similarity(unaccent(LOWER(p.specific_name)), unaccent(LOWER(:keyword))) > 0.4
               OR similarity(unaccent(LOWER(COALESCE(p.brand, ''))), unaccent(LOWER(:keyword))) > 0.4
            ORDER BY GREATEST(
                    similarity(unaccent(LOWER(p.generic_name)), unaccent(LOWER(:keyword))),
                    similarity(unaccent(LOWER(p.specific_name)), unaccent(LOWER(:keyword))),
                    similarity(unaccent(LOWER(COALESCE(p.brand, ''))), unaccent(LOWER(:keyword)))
                ) DESC,
                p.purchase_count DESC
            LIMIT 10
            """, nativeQuery = true)
    List<ProductCatalog> searchByKeywordFuzzy(@Param("keyword") String keyword);
}