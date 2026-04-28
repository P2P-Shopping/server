package com.p2ps.lists.repo;

import com.p2ps.lists.model.ShoppingList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ShoppingListRepository extends JpaRepository<ShoppingList, UUID> {
    List<ShoppingList> findByUser_Email(String email);

    @Query("SELECT DISTINCT l FROM ShoppingList l LEFT JOIN FETCH l.user LEFT JOIN FETCH l.collaborators c WHERE l.user.email = :email OR c.email = :email")
    List<ShoppingList> findAccessibleByEmail(@Param("email") String email);

    @Query("SELECT COUNT(l) > 0 FROM ShoppingList l LEFT JOIN l.collaborators c WHERE l.id = :id AND (l.user.email = :email OR c.email = :email)")
    boolean existsByIdAndUserEmailOrCollaboratorEmail(@Param("id") UUID id, @Param("email") String email);
}