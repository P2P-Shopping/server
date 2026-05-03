package com.p2ps.lists.repo;

import com.p2ps.lists.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    interface UserProductHistoryMatch {
        String getItemName();
        UUID getCatalogId();
        String getCatalogGenericName();
        String getCatalogSpecificName();
        String getBrand();
        String getCategory();
    }

    @Query(value = """
            SELECT
                i.name AS itemName,
                c.id AS catalogId,
                c.generic_name AS catalogGenericName,
                c.specific_name AS catalogSpecificName,
                COALESCE(c.brand, i.brand) AS brand,
                COALESCE(c.category, i.category) AS category
            FROM items i
            JOIN shopping_lists sl ON sl.id = i.list_id
            LEFT JOIN shopping_list_collaborators slc ON slc.shopping_list_id = sl.id
            LEFT JOIN p2p_product_catalog c ON c.id = i.catalog_id
            WHERE (sl.user_id = :userId OR slc.user_id = :userId)
              AND (
                    LOWER(i.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(i.name) % LOWER(:keyword)
                 OR LOWER(COALESCE(c.generic_name, '')) % LOWER(:keyword)
                 OR LOWER(COALESCE(c.specific_name, '')) % LOWER(:keyword)
              )
            ORDER BY GREATEST(
                    similarity(LOWER(i.name), LOWER(:keyword)),
                    similarity(LOWER(COALESCE(c.generic_name, '')), LOWER(:keyword)),
                    similarity(LOWER(COALESCE(c.specific_name, '')), LOWER(:keyword))
                ) DESC,
                i.last_updated_timestamp DESC NULLS LAST
            LIMIT 10
            """, nativeQuery = true)
    List<UserProductHistoryMatch> findUserProductHistoryMatches(
            @Param("userId") Integer userId,
            @Param("keyword") String keyword
    );
}
