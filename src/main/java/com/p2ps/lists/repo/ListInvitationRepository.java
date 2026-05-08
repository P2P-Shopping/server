package com.p2ps.lists.repo;

import com.p2ps.lists.model.InvitationStatus;
import com.p2ps.lists.model.ListInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ListInvitationRepository extends JpaRepository<ListInvitation, UUID> {

    @Query("SELECT i FROM ListInvitation i LEFT JOIN FETCH i.shoppingList LEFT JOIN FETCH i.inviter WHERE i.invitee.email = :email AND i.status = :status")
    List<ListInvitation> findByInviteeEmailAndStatus(@Param("email") String email, @Param("status") InvitationStatus status);

    Optional<ListInvitation> findByShoppingListIdAndInviteeId(UUID listId, Integer inviteeId);

    boolean existsByShoppingListIdAndInviteeIdAndStatus(UUID listId, Integer inviteeId, InvitationStatus status);
}
