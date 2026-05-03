package com.p2ps.lists.repo;

import com.p2ps.test.AbstractRepositoryTest;
import com.p2ps.auth.model.User;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.lists.model.Item;
import com.p2ps.lists.model.ShoppingList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ItemRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private ItemRepository itemRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = entityManager.persistAndFlush(new User("test@user.com", "pass"));

        ShoppingList list1 = new ShoppingList("List 1", user);
        entityManager.persistAndFlush(list1);

        ShoppingList list2 = new ShoppingList("List 2", user);
        entityManager.persistAndFlush(list2);

        ProductCatalog ouaCatalog = new ProductCatalog();
        ouaCatalog.setGenericName("oua");
        ouaCatalog.setSpecificName("Oua de gaina M");
        ouaCatalog.setBrand("Ferma");
        ouaCatalog.setCategory("Lactate");
        entityManager.persistAndFlush(ouaCatalog);

        Item item1 = new Item("oua proaspete", list1);
        item1.setCatalogId(ouaCatalog.getId());
        entityManager.persistAndFlush(item1);

        Item item2 = new Item("lapte", list1);
        entityManager.persistAndFlush(item2);

        Item item3 = new Item("paine", list2);
        entityManager.persistAndFlush(item3);
    }

    @Test
    void findUserProductHistoryMatches_fuzzyMatchOnItemName_returnsMatch() {
        // Search for "ou", which should match "oua proaspete"
        List<ItemRepository.UserProductHistoryMatch> results = itemRepository.findUserProductHistoryMatches(user.getId(), "ou");

        assertThat(results).hasSize(1);
        ItemRepository.UserProductHistoryMatch match = results.get(0);
        assertThat(match.getItemName()).isEqualTo("oua proaspete");
        assertThat(match.getCatalogGenericName()).isEqualTo("oua");
        assertThat(match.getCatalogSpecificName()).isEqualTo("Oua de gaina M");
    }

    @Test
    void findUserProductHistoryMatches_exactMatch_returnsMatch() {
        List<ItemRepository.UserProductHistoryMatch> results = itemRepository.findUserProductHistoryMatches(user.getId(), "lapte");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getItemName()).isEqualTo("lapte");
    }

    @Test
    void findUserProductHistoryMatches_noMatch_returnsEmptyList() {
        List<ItemRepository.UserProductHistoryMatch> results = itemRepository.findUserProductHistoryMatches(user.getId(), "branza");

        assertThat(results).isEmpty();
    }

    @Test
    void findUserProductHistoryMatches_forDifferentUser_returnsEmptyList() {
        User otherUser = new User("other@user.com", "pass");
        entityManager.persistAndFlush(otherUser);

        List<ItemRepository.UserProductHistoryMatch> results = itemRepository.findUserProductHistoryMatches(otherUser.getId(), "ou");

        assertThat(results).isEmpty();
    }
}
