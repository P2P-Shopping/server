package com.p2ps.lists.model;

import com.p2ps.auth.model.Users;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ListCollaboratorTest {

    @Test
    void defaultConstructor_shouldInitializeDefaults() {
        ListCollaborator collab = new ListCollaborator();
        assertThat(collab.getShoppingList()).isNull();
        assertThat(collab.getUser()).isNull();
        assertThat(collab.getRole()).isEqualTo(ListRole.EDITOR);
    }

    @Test
    void parameterizedConstructor_shouldSetAllFields() {
        Users user = new Users();
        user.setId(1);
        ShoppingList list = new ShoppingList();
        list.setId(UUID.randomUUID());

        ListCollaborator collab = new ListCollaborator(list, user, ListRole.EDITOR);

        assertThat(collab.getShoppingList()).isEqualTo(list);
        assertThat(collab.getUser()).isEqualTo(user);
        assertThat(collab.getRole()).isEqualTo(ListRole.EDITOR);
    }

    @Test
    void getShoppingListId_shouldReturnNullWhenShoppingListIsNull() {
        ListCollaborator collab = new ListCollaborator();
        assertThat(collab.getShoppingListId()).isNull();
    }

    @Test
    void getShoppingListId_shouldReturnIdWhenShoppingListExists() {
        UUID listId = UUID.randomUUID();
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        Users user = new Users();
        user.setId(1);

        ListCollaborator collab = new ListCollaborator(list, user, ListRole.EDITOR);
        assertThat(collab.getShoppingListId()).isEqualTo(listId);
    }

    @Test
    void getUserId_shouldReturnNullWhenUserIsNull() {
        ListCollaborator collab = new ListCollaborator();
        assertThat(collab.getUserId()).isNull();
    }

    @Test
    void getUserId_shouldReturnIdWhenUserExists() {
        Users user = new Users();
        user.setId(42);
        ShoppingList list = new ShoppingList();
        list.setId(UUID.randomUUID());

        ListCollaborator collab = new ListCollaborator(list, user, ListRole.EDITOR);
        assertThat(collab.getUserId()).isEqualTo(42);
    }

    @Test
    void listCollaboratorId_defaultConstructor() {
        ListCollaborator.ListCollaboratorId id = new ListCollaborator.ListCollaboratorId();
        assertThat(id).isNotNull();
    }

    @Test
    void listCollaboratorId_parameterizedConstructor() {
        Users user = new Users();
        user.setId(1);
        ShoppingList list = new ShoppingList();
        list.setId(UUID.randomUUID());

        ListCollaborator.ListCollaboratorId id = new ListCollaborator.ListCollaboratorId(list, user);
        assertThat(id).isNotNull();
    }

    @Test
    void listCollaboratorId_equals_sameInstance() {
        Users user = new Users();
        user.setId(1);
        ShoppingList list = new ShoppingList();
        list.setId(UUID.randomUUID());

        ListCollaborator.ListCollaboratorId id = new ListCollaborator.ListCollaboratorId(list, user);
        assertThat(id.equals(id)).isTrue();
    }

    @Test
    void listCollaboratorId_equals_null() {
        ListCollaborator.ListCollaboratorId id = new ListCollaborator.ListCollaboratorId();
        assertThat(id.equals(null)).isFalse();
    }

    @Test
    void listCollaboratorId_equals_differentClass() {
        ListCollaborator.ListCollaboratorId id = new ListCollaborator.ListCollaboratorId();
        assertThat(id.equals("not an id")).isFalse();
    }

    @Test
    void listCollaboratorId_equals_equalIds() {
        UUID listId = UUID.randomUUID();
        Users user1 = new Users();
        user1.setId(1);
        Users user2 = new Users();
        user2.setId(1);
        ShoppingList list1 = new ShoppingList();
        list1.setId(listId);
        ShoppingList list2 = new ShoppingList();
        list2.setId(listId);

        ListCollaborator.ListCollaboratorId id1 = new ListCollaborator.ListCollaboratorId(list1, user1);
        ListCollaborator.ListCollaboratorId id2 = new ListCollaborator.ListCollaboratorId(list2, user2);

        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void listCollaboratorId_notEqual_differentListId() {
        Users user = new Users();
        user.setId(1);
        ShoppingList list1 = new ShoppingList();
        list1.setId(UUID.randomUUID());
        ShoppingList list2 = new ShoppingList();
        list2.setId(UUID.randomUUID());

        ListCollaborator.ListCollaboratorId id1 = new ListCollaborator.ListCollaboratorId(list1, user);
        ListCollaborator.ListCollaboratorId id2 = new ListCollaborator.ListCollaboratorId(list2, user);

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void listCollaboratorId_notEqual_differentUserId() {
        UUID listId = UUID.randomUUID();
        Users user1 = new Users();
        user1.setId(1);
        Users user2 = new Users();
        user2.setId(2);
        ShoppingList list = new ShoppingList();
        list.setId(listId);

        ListCollaborator.ListCollaboratorId id1 = new ListCollaborator.ListCollaboratorId(list, user1);
        ListCollaborator.ListCollaboratorId id2 = new ListCollaborator.ListCollaboratorId(list, user2);

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void listCollaboratorId_equals_bothNullFields() {
        ListCollaborator.ListCollaboratorId id1 = new ListCollaborator.ListCollaboratorId();
        ListCollaborator.ListCollaboratorId id2 = new ListCollaborator.ListCollaboratorId();
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void listCollaboratorId_equals_oneNullField() {
        Users user = new Users();
        user.setId(1);
        ShoppingList list = new ShoppingList();
        list.setId(UUID.randomUUID());

        ListCollaborator.ListCollaboratorId id1 = new ListCollaborator.ListCollaboratorId(list, user);
        ListCollaborator.ListCollaboratorId id2 = new ListCollaborator.ListCollaboratorId(null, user);

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void listCollaboratorId_hashCode_equalIds() {
        UUID listId = UUID.randomUUID();
        Users user1 = new Users();
        user1.setId(1);
        Users user2 = new Users();
        user2.setId(1);
        ShoppingList list1 = new ShoppingList();
        list1.setId(listId);
        ShoppingList list2 = new ShoppingList();
        list2.setId(listId);

        ListCollaborator.ListCollaboratorId id1 = new ListCollaborator.ListCollaboratorId(list1, user1);
        ListCollaborator.ListCollaboratorId id2 = new ListCollaborator.ListCollaboratorId(list2, user2);

        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    void listCollaboratorId_hashCode_nullFields() {
        ListCollaborator.ListCollaboratorId id1 = new ListCollaborator.ListCollaboratorId();
        ListCollaborator.ListCollaboratorId id2 = new ListCollaborator.ListCollaboratorId();
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    void listCollaboratorId_hashCode_differentIds() {
        Users user1 = new Users();
        user1.setId(1);
        Users user2 = new Users();
        user2.setId(2);
        ShoppingList list = new ShoppingList();
        list.setId(UUID.randomUUID());

        ListCollaborator.ListCollaboratorId id1 = new ListCollaborator.ListCollaboratorId(list, user1);
        ListCollaborator.ListCollaboratorId id2 = new ListCollaborator.ListCollaboratorId(list, user2);

        assertThat(id1.hashCode()).isNotEqualTo(id2.hashCode());
    }
}
