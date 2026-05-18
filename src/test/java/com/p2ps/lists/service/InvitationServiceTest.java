package com.p2ps.lists.service;

import com.p2ps.auth.model.Users;
import com.p2ps.lists.dto.ListInvitationDTO;
import com.p2ps.lists.exception.InvitationNotFoundException;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.model.InvitationStatus;
import com.p2ps.lists.model.ListCollaborator;
import com.p2ps.lists.model.ListInvitation;
import com.p2ps.lists.model.ShoppingList;
import com.p2ps.lists.repo.ListInvitationRepository;
import com.p2ps.lists.repo.ShoppingListRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private ListInvitationRepository invitationRepository;

    @Mock
    private ShoppingListRepository shoppingListRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private InvitationService invitationService;

    private Users createUser(Integer id, String email) {
        Users user = new Users(email, "pass", "First", "Last");
        user.setId(id);
        return user;
    }

    private ShoppingList createList(UUID id, Users owner) {
        ShoppingList list = new ShoppingList();
        list.setId(id);
        list.setTitle("Test List");
        list.setUser(owner);
        return list;
    }

    private ListInvitation createInvitation(UUID id, ShoppingList list, Users inviter, Users invitee, InvitationStatus status) {
        ListInvitation inv = new ListInvitation();
        inv.setId(id);
        inv.setShoppingList(list);
        inv.setInviter(inviter);
        inv.setInvitee(invitee);
        inv.setStatus(status);
        return inv;
    }

    @Test
    void getPendingInvitationsShouldReturnOnlyPendingForInvitee() {
        String inviteeEmail = "invitee@example.com";
        UUID listId = UUID.randomUUID();
        Users owner = createUser(1, "owner@example.com");
        Users invitee = createUser(2, inviteeEmail);

        ShoppingList list = createList(listId, owner);
        UUID invId = UUID.randomUUID();
        ListInvitation pending = createInvitation(invId, list, owner, invitee, InvitationStatus.PENDING);

        when(invitationRepository.findByInviteeEmailAndStatus(inviteeEmail, InvitationStatus.PENDING))
                .thenReturn(List.of(pending));

        List<ListInvitationDTO> result = invitationService.getPendingInvitations(inviteeEmail);

        assertEquals(1, result.size());
        ListInvitationDTO dto = result.get(0);
        assertEquals(invId, dto.getId());
        assertEquals(listId, dto.getListId());
        assertEquals("Test List", dto.getListTitle());
        assertEquals("First Last", dto.getInviterName());
        assertEquals("o***@example.com", dto.getInviterEmail());
        assertEquals(InvitationStatus.PENDING, dto.getStatus());
        assertEquals(pending.getCreatedAt(), dto.getCreatedAt());
    }

    @Test
    void getPendingInvitationsShouldReturnMultipleResults() {
        String inviteeEmail = "invitee@example.com";
        Users owner = createUser(1, "owner@example.com");
        Users invitee = createUser(2, inviteeEmail);

        ShoppingList list1 = createList(UUID.randomUUID(), owner);
        ShoppingList list2 = createList(UUID.randomUUID(), owner);
        ListInvitation inv1 = createInvitation(UUID.randomUUID(), list1, owner, invitee, InvitationStatus.PENDING);
        ListInvitation inv2 = createInvitation(UUID.randomUUID(), list2, owner, invitee, InvitationStatus.PENDING);

        when(invitationRepository.findByInviteeEmailAndStatus(inviteeEmail, InvitationStatus.PENDING))
                .thenReturn(List.of(inv1, inv2));

        List<ListInvitationDTO> result = invitationService.getPendingInvitations(inviteeEmail);

        assertEquals(2, result.size());
        assertEquals(list1.getId(), result.get(0).getListId());
        assertEquals(list2.getId(), result.get(1).getListId());
    }

    @Test
    void getPendingInvitationsShouldReturnEmptyWhenNone() {
        when(invitationRepository.findByInviteeEmailAndStatus("nobody@example.com", InvitationStatus.PENDING))
                .thenReturn(List.of());

        List<ListInvitationDTO> result = invitationService.getPendingInvitations("nobody@example.com");

        assertTrue(result.isEmpty());
    }

    @Test
    void acceptInvitationShouldAddCollaboratorAndSetAccepted() {
        UUID invId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        Users owner = createUser(1, "owner@example.com");
        Users invitee = createUser(2, "invitee@example.com");
        ShoppingList list = createList(listId, owner);

        ListInvitation invitation = createInvitation(invId, list, owner, invitee, InvitationStatus.PENDING);

        when(invitationRepository.findById(invId)).thenReturn(Optional.of(invitation));
        when(shoppingListRepository.save(any(ShoppingList.class))).thenAnswer(i -> i.getArgument(0));
        when(invitationRepository.save(any(ListInvitation.class))).thenAnswer(i -> i.getArgument(0));

        invitationService.acceptInvitation(invId, "invitee@example.com");

        assertEquals(1, list.getCollaborators().size());
        ListCollaborator added = list.getCollaborators().iterator().next();
        assertEquals(invitee.getId(), added.getUserId());
        assertEquals(InvitationStatus.ACCEPTED, invitation.getStatus());
        verify(shoppingListRepository).save(list);
        verify(invitationRepository).save(invitation);
    }

    @Test
    void acceptInvitationShouldThrowForNonInvitee() {
        UUID invId = UUID.randomUUID();
        Users owner = createUser(1, "owner@example.com");
        Users invitee = createUser(2, "invitee@example.com");
        ShoppingList list = createList(UUID.randomUUID(), owner);
        ListInvitation invitation = createInvitation(invId, list, owner, invitee, InvitationStatus.PENDING);

        when(invitationRepository.findById(invId)).thenReturn(Optional.of(invitation));

        assertThrows(ListAccessDeniedException.class,
                () -> invitationService.acceptInvitation(invId, "wrong@example.com"));
    }

    @Test
    void acceptInvitationShouldThrowWhenNotPending() {
        UUID invId = UUID.randomUUID();
        Users owner = createUser(1, "owner@example.com");
        Users invitee = createUser(2, "invitee@example.com");
        ShoppingList list = createList(UUID.randomUUID(), owner);
        ListInvitation invitation = createInvitation(invId, list, owner, invitee, InvitationStatus.ACCEPTED);

        when(invitationRepository.findById(invId)).thenReturn(Optional.of(invitation));

        assertThrows(ListAccessDeniedException.class,
                () -> invitationService.acceptInvitation(invId, "invitee@example.com"));
    }

    @Test
    void acceptInvitationShouldThrowWhenNotFound() {
        when(invitationRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        UUID randomId = UUID.randomUUID();
        assertThrows(InvitationNotFoundException.class,
                () -> invitationService.acceptInvitation(randomId, "invitee@example.com"));
    }

    @Test
    void declineInvitationShouldSetDeclinedAndNotModifyList() {
        UUID invId = UUID.randomUUID();
        Users owner = createUser(1, "owner@example.com");
        Users invitee = createUser(2, "invitee@example.com");
        ShoppingList list = createList(UUID.randomUUID(), owner);
        ListInvitation invitation = createInvitation(invId, list, owner, invitee, InvitationStatus.PENDING);

        when(invitationRepository.findById(invId)).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any(ListInvitation.class))).thenAnswer(i -> i.getArgument(0));

        invitationService.declineInvitation(invId, "invitee@example.com");

        assertEquals(InvitationStatus.DECLINED, invitation.getStatus());
        assertTrue(list.getCollaborators().isEmpty());
        verify(shoppingListRepository, never()).save(any(ShoppingList.class));
    }

    @Test
    void declineInvitationShouldThrowForNonInvitee() {
        UUID invId = UUID.randomUUID();
        Users owner = createUser(1, "owner@example.com");
        Users invitee = createUser(2, "invitee@example.com");
        ShoppingList list = createList(UUID.randomUUID(), owner);
        ListInvitation invitation = createInvitation(invId, list, owner, invitee, InvitationStatus.PENDING);

        when(invitationRepository.findById(invId)).thenReturn(Optional.of(invitation));

        assertThrows(ListAccessDeniedException.class,
                () -> invitationService.declineInvitation(invId, "wrong@example.com"));
    }

    @Test
    void declineInvitationShouldThrowWhenNotPending() {
        UUID invId = UUID.randomUUID();
        Users owner = createUser(1, "owner@example.com");
        Users invitee = createUser(2, "invitee@example.com");
        ShoppingList list = createList(UUID.randomUUID(), owner);
        ListInvitation invitation = createInvitation(invId, list, owner, invitee, InvitationStatus.DECLINED);

        when(invitationRepository.findById(invId)).thenReturn(Optional.of(invitation));

        assertThrows(ListAccessDeniedException.class,
                () -> invitationService.declineInvitation(invId, "invitee@example.com"));
    }

    @Test
    void declineInvitationShouldThrowWhenNotFound() {
        when(invitationRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        UUID randomId = UUID.randomUUID();
        assertThrows(InvitationNotFoundException.class,
                () -> invitationService.declineInvitation(randomId, "invitee@example.com"));
    }
}
