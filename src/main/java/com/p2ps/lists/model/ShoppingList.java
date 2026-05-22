package com.p2ps.lists.model;

import com.p2ps.auth.model.Users;
import com.p2ps.store.model.StoreGeofence;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "shopping_lists")
@Getter
@Setter
public class ShoppingList {

    public ShoppingList() {}

    public ShoppingList(String title, Users user) {
        this.title = title;
        this.user = user;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50, nullable = false)
    private ListCategory category = ListCategory.NORMAL;

    @Column(name = "subcategory", length = 100)
    private String subcategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "final_store_id")
    private StoreGeofence finalStore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @OneToMany(mappedBy = "shoppingList", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ListCollaborator> collaborators = new HashSet<>();

    //sterge itemi din lista cand sterge o lista
    @OneToMany(mappedBy = "shoppingList", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Item> items = new ArrayList<>();

    public boolean canBeModifiedBy(String email) {
        if (user == null) return false;
        return user.getEmail().equals(email) || collaborators.stream()
                .anyMatch(c -> c.getUser().getEmail().equals(email));
    }

    public Optional<ListCollaborator> getCollaboratorByUserEmail(String email) {
        return collaborators.stream()
                .filter(c -> c.getUser().getEmail().equals(email))
                .findFirst();
    }

    public boolean hasCollaborator(Integer userId) {
        return collaborators.stream()
                .anyMatch(c -> c.getUser().getId().equals(userId));
    }

    public boolean removeCollaboratorByUserId(Integer userId) {
        return collaborators.removeIf(c -> c.getUser().getId().equals(userId));
    }
}
