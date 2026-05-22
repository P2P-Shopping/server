package com.p2ps.shopping.repository;

import com.p2ps.shopping.model.ShoppingSession;
import com.p2ps.shopping.model.ShoppingSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShoppingSessionRepository extends JpaRepository<ShoppingSession, UUID> {

    Optional<ShoppingSession> findFirstByShoppingList_IdAndStatusOrderByStartedAtDesc(UUID listId,
                                                                                       ShoppingSessionStatus status);

    List<ShoppingSession> findByShoppingList_IdAndStatus(UUID listId, ShoppingSessionStatus status);
}
