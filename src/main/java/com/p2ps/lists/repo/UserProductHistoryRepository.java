package com.p2ps.lists.repo;

import com.p2ps.lists.model.UserProductHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface UserProductHistoryRepository extends JpaRepository<UserProductHistory, UUID> {

    interface HistoryMatch {
        String getItemName();
        UUID getCatalogId();
        String getCatalogGenericName();
        String getCatalogSpecificName();
        String getBrand();
        String getCategory();
        BigDecimal getPrice();
    }

    // 1. Am unificat interfața aici. Are toate câmpurile de care are nevoie AI-ul!
    interface PopularUnknownProduct {
        String getCustomName();
        Integer getUserCount();
        String getBrand();
        String getCategory();
        BigDecimal getPrice();
        String getStoreName();
    }

    @Query(value = """
            SELECT
                h.custom_name AS itemName,
                c.id AS catalogId,
                c.generic_name AS catalogGenericName,
                c.specific_name AS catalogSpecificName,
                c.brand AS brand,
                c.category AS category,
                c.estimated_price AS price
            FROM user_product_history h
            LEFT JOIN p2p_product_catalog c ON c.id = h.catalog_id
            WHERE h.user_id = :userId
              AND (
                    f_unaccent(LOWER(h.custom_name)) LIKE f_unaccent(LOWER(CONCAT('%', :keyword, '%')))
                 OR similarity(f_unaccent(LOWER(h.custom_name)), f_unaccent(LOWER(:keyword))) > 0.4
                 OR similarity(f_unaccent(LOWER(COALESCE(c.generic_name, ''))), f_unaccent(LOWER(:keyword))) > 0.4
                 OR similarity(f_unaccent(LOWER(COALESCE(c.specific_name, ''))), f_unaccent(LOWER(:keyword))) > 0.4
              )
            ORDER BY GREATEST(
                    similarity(f_unaccent(LOWER(h.custom_name)), f_unaccent(LOWER(:keyword))),
                    similarity(f_unaccent(LOWER(COALESCE(c.generic_name, ''))), f_unaccent(LOWER(:keyword))),
                    similarity(f_unaccent(LOWER(COALESCE(c.specific_name, ''))), f_unaccent(LOWER(:keyword)))
                ) DESC,
                h.last_added_timestamp DESC NULLS LAST
            LIMIT 10
            """, nativeQuery = true)
    List<HistoryMatch> findMatches(@Param("userId") Integer userId, @Param("keyword") String keyword);

    // 2. Am legat Query-ul corect de numele metodei pe care o așteaptă Service-ul
    @Query("SELECT lower(trim(u.customName)) as customName, COUNT(DISTINCT u.user.id) as userCount, " +
            "MAX(u.brand) as brand, MAX(u.category) as category, AVG(u.price) as price, MAX(u.storeName) as storeName " +
            "FROM UserProductHistory u WHERE u.catalogItem IS NULL " +
            "GROUP BY lower(trim(u.customName)) HAVING COUNT(DISTINCT u.user.id) >= :minUsers")
    List<PopularUnknownProduct> findPopularUnknownProducts(@Param("minUsers") int minUsers);

    @Modifying
    @Query("""
            update UserProductHistory h
            set h.catalogItem = :catalogItem
            where lower(h.customName) = lower(:customName)
              and h.catalogItem is null
            """)
    int linkUnknownHistoryToCatalog(@Param("customName") String customName,
                                    @Param("catalogItem") com.p2ps.catalog.model.ProductCatalog catalogItem);

    UserProductHistory findByUser_IdAndCustomNameIgnoreCase(Integer userId, String customName);
}
