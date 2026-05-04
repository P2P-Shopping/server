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


   
}
