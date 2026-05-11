package com.p2ps.lists.service;

import com.p2ps.auth.model.Users;
import com.p2ps.lists.dto.ListInvitationDTO;
import com.p2ps.lists.exception.InvitationNotFoundException;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.exception.ListUserNotFoundException;
import com.p2ps.lists.model.InvitationStatus;
import com.p2ps.lists.model.ListInvitation;
import com.p2ps.lists.model.ShoppingList;
import com.p2ps.lists.repo.ListInvitationRepository;
import com.p2ps.lists.repo.ShoppingListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class InvitationService {

    private final ListInvitationRepository invitationRepository;
    private final ShoppingListRepository shoppingListRepository;

    public InvitationService(ListInvitationRepository invitationRepository,
                             ShoppingListRepository shoppingListRepository) {
        this.invitationRepository = invitationRepository;
        this.shoppingListRepository = shoppingListRepository;
    }

    @Transactional(readOnly = true)
    public List<ListInvitationDTO> getPendingInvitations(String userEmail) {
        return invitationRepository.findByInviteeEmailAndStatus(userEmail, InvitationStatus.PENDING)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional
    public void acceptInvitation(UUID invitationId, String userEmail) {
        ListInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new InvitationNotFoundException("Invitation not found"));

        if (!invitation.getInvitee().getEmail().equals(userEmail)) {
            throw new ListAccessDeniedException("This invitation is not for you");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ListAccessDeniedException("Invitation is no longer pending");
        }

        ShoppingList list = invitation.getShoppingList();
        list.getCollaborators().add(invitation.getInvitee());
        shoppingListRepository.save(list);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);
    }

    @Transactional
    public void declineInvitation(UUID invitationId, String userEmail) {
        ListInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new InvitationNotFoundException("Invitation not found"));

        if (!invitation.getInvitee().getEmail().equals(userEmail)) {
            throw new ListAccessDeniedException("This invitation is not for you");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ListAccessDeniedException("Invitation is no longer pending");
        }

        invitation.setStatus(InvitationStatus.DECLINED);
        invitationRepository.save(invitation);
    }

    private ListInvitationDTO mapToDTO(ListInvitation invitation) {
        ListInvitationDTO dto = new ListInvitationDTO();
        dto.setId(invitation.getId());
        dto.setListId(invitation.getShoppingList().getId());
        dto.setListTitle(invitation.getShoppingList().getTitle());
        dto.setInviterName(invitation.getInviter().getFirstName() + " " + invitation.getInviter().getLastName());
        String email = invitation.getInviter().getEmail();
        dto.setInviterEmail(email.replaceAll("(^.)[^@]*(@.*$)", "$1***$2"));
        dto.setStatus(invitation.getStatus());
        dto.setCreatedAt(invitation.getCreatedAt());
        return dto;
    }
}
