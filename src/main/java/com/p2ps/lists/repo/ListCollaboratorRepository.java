package com.p2ps.lists.repo;

import com.p2ps.lists.model.ListCollaborator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ListCollaboratorRepository extends JpaRepository<ListCollaborator, ListCollaborator.ListCollaboratorId> {

    @Modifying
    @Query("DELETE FROM ListCollaborator lc WHERE lc.shoppingList.id = :listId AND lc.user.id = :userId")
    void deleteByListIdAndUserId(@Param("listId") UUID listId, @Param("userId") Integer userId);

    @Query("SELECT COUNT(lc) > 0 FROM ListCollaborator lc WHERE lc.shoppingList.id = :listId AND lc.user.id = :userId")
    boolean existsByListIdAndUserId(@Param("listId") UUID listId, @Param("userId") Integer userId);
}
