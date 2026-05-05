package com.p2ps.lists.repo;

import com.p2ps.lists.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {
    List<Item> findByShoppingListIdAndNameIgnoreCase(UUID shoppingListId, String name);

    interface UserProductHistoryMatch {
        String getItemName();
        UUID getCatalogId();
        String getCatalogGenericName();
        String getCatalogSpecificName();
        String getBrand();
        String getCategory();
    }
}
