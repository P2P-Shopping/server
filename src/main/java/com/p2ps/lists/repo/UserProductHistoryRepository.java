package com.p2ps.lists.repo;

import com.p2ps.lists.model.UserProductHistory;
import org.springframework.data.jpa.repository.JpaRepository;
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
                    LOWER(h.custom_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(h.custom_name) % LOWER(:keyword)
                 OR LOWER(COALESCE(c.generic_name, '')) % LOWER(:keyword)
                 OR LOWER(COALESCE(c.specific_name, '')) % LOWER(:keyword)
              )
            ORDER BY GREATEST(
                    similarity(LOWER(h.custom_name), LOWER(:keyword)),
                    similarity(LOWER(COALESCE(c.generic_name, '')), LOWER(:keyword)),
                    similarity(LOWER(COALESCE(c.specific_name, '')), LOWER(:keyword))
                ) DESC,
                h.last_added_timestamp DESC NULLS LAST
            LIMIT 10
            """, nativeQuery = true)
    List<HistoryMatch> findMatches(@Param("userId") Integer userId, @Param("keyword") String keyword);

    UserProductHistory findByUser_IdAndCustomNameIgnoreCase(Integer userId, String customName);
}