package com.p2ps.lists.repo;



import com.p2ps.lists.model.ShoppingList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ShoppingListRepository extends JpaRepository<ShoppingList, UUID> {
    List<ShoppingList> findByUser_Email(String email);
    List<ShoppingList> findByUser_EmailOrCollaborators_Email(String ownerEmail, String collaboratorEmail);
}