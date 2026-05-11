package com.p2ps.lists.repo;

import com.p2ps.lists.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {
    List<Item> findByShoppingListIdAndNameIgnoreCase(UUID shoppingListId, String name);
    List<Item> findByShoppingListIdAndCatalogItem_Id(UUID shoppingListId, UUID catalogId);
    List<Item> findByShoppingListIdAndIsCheckedTrue(UUID shoppingListId);

    @Query(value = """
        SELECT i.external_item_id
        FROM items i
        WHERE i.external_item_id IS NOT NULL
          AND (
              LOWER(i.external_item_id) = LOWER(:itemName)
              OR LOWER(i.name) = LOWER(:itemName)
              OR LOWER(i.name) = LOWER(CONCAT('Imported Item ', :itemName))
          )
          AND (
              EXISTS (SELECT 1 FROM raw_user_pings rup WHERE rup.item_id = i.id)
              OR EXISTS (SELECT 1 FROM store_inventory_map sim WHERE sim.item_id = i.id)
          )
        ORDER BY CASE
            WHEN LOWER(i.external_item_id) = LOWER(:itemName) THEN 0
            WHEN LOWER(i.name) = LOWER(:itemName) THEN 1
            ELSE 2
        END
        LIMIT 1
        """, nativeQuery = true)
    Optional<String> findRoutableExternalItemIdByName(@Param("itemName") String itemName);

    interface UserProductHistoryMatch {
        String getItemName();
        UUID getCatalogId();
        String getCatalogGenericName();
        String getCatalogSpecificName();
        String getBrand();
        String getCategory();
    }
}
