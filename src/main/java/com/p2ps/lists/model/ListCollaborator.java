package com.p2ps.lists.model;

import com.p2ps.auth.model.Users;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "shopping_list_collaborators")
@Getter
@Setter
@IdClass(ListCollaborator.ListCollaboratorId.class)
public class ListCollaborator {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shopping_list_id", nullable = false)
    private ShoppingList shoppingList;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private ListRole role = ListRole.EDITOR;

    public ListCollaborator() {}

    public ListCollaborator(ShoppingList shoppingList, Users user, ListRole role) {
        this.shoppingList = shoppingList;
        this.user = user;
        this.role = role;
    }

    public UUID getShoppingListId() {
        return shoppingList != null ? shoppingList.getId() : null;
    }

    public Integer getUserId() {
        return user != null ? user.getId() : null;
    }

    public static class ListCollaboratorId implements Serializable {
        private transient ShoppingList shoppingList;
        private transient Users user;

        public ListCollaboratorId() {}

        public ListCollaboratorId(ShoppingList shoppingList, Users user) {
            this.shoppingList = shoppingList;
            this.user = user;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ListCollaboratorId that = (ListCollaboratorId) o;
            return Objects.equals(
                    shoppingList != null ? shoppingList.getId() : null,
                    that.shoppingList != null ? that.shoppingList.getId() : null) &&
                    Objects.equals(
                    user != null ? user.getId() : null,
                    that.user != null ? that.user.getId() : null);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    shoppingList != null ? shoppingList.getId() : null,
                    user != null ? user.getId() : null);
        }
    }
}
