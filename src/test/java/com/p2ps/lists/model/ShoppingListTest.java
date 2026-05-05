package com.p2ps.lists.model;

import com.p2ps.auth.model.Users;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ShoppingListTest {

    @Test
    void testGettersSetters() {
        ShoppingList list = new ShoppingList();
        UUID id = UUID.randomUUID();
        list.setId(id);
        list.setTitle("Weekly");
        
        Users user = new Users();
        user.setEmail("owner@test.com");
        list.setUser(user);
        
        list.setItems(new ArrayList<>());
        list.setCategory(ListCategory.RECIPE);
        list.setSubcategory("Fruits");
        list.setFinalStore("Lidl");
        list.setCollaborators(new java.util.HashSet<>());
        
        assertThat(list.getId()).isEqualTo(id);
        assertThat(list.getTitle()).isEqualTo("Weekly");
        assertThat(list.getUser().getEmail()).isEqualTo("owner@test.com");
        assertThat(list.getItems()).isEmpty();
        assertThat(list.getCategory()).isEqualTo(ListCategory.RECIPE);
        assertThat(list.getSubcategory()).isEqualTo("Fruits");
        assertThat(list.getFinalStore()).isEqualTo("Lidl");
        assertThat(list.getCollaborators()).isEmpty();
    }

    @Test
    void canBeModifiedBy_whenUserMatches_returnsTrue() {
        Users owner = new Users();
        owner.setEmail("owner@test.com");
        
        ShoppingList list = new ShoppingList();
        list.setUser(owner);
        
        assertThat(list.canBeModifiedBy("owner@test.com")).isTrue();
    }

    @Test
    void canBeModifiedBy_whenUserMismatch_returnsFalse() {
        Users owner = new Users();
        owner.setEmail("owner@test.com");
        
        ShoppingList list = new ShoppingList();
        list.setUser(owner);
        
        assertThat(list.canBeModifiedBy("hacker@test.com")).isFalse();
    }

    @Test
    void canBeModifiedBy_whenCollaboratorMatches_returnsTrue() {
        Users owner = new Users();
        owner.setEmail("owner@test.com");
        Users collaborator = new Users();
        collaborator.setEmail("collab@test.com");
        
        ShoppingList list = new ShoppingList();
        list.setUser(owner);
        list.getCollaborators().add(collaborator);
        
        assertThat(list.canBeModifiedBy("collab@test.com")).isTrue();
    }

    @Test
    void canBeModifiedBy_whenUserIsNull_returnsFalse() {
        ShoppingList list = new ShoppingList();
        assertThat(list.canBeModifiedBy("any@test.com")).isFalse();
    }

    @Test
    void constructor_withTitleAndUser_setsFields() {
        Users user = new Users();
        ShoppingList list = new ShoppingList("Party", user);
        assertThat(list.getTitle()).isEqualTo("Party");
        assertThat(list.getUser()).isEqualTo(user);
    }
}
