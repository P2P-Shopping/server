package com.p2ps.lists.controller;

import com.p2ps.lists.dto.ListInvitationDTO;
import com.p2ps.lists.service.InvitationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping
    public ResponseEntity<List<ListInvitationDTO>> getPendingInvitations(Authentication authentication) {
        List<ListInvitationDTO> invitations = invitationService.getPendingInvitations(authentication.getName());
        return ResponseEntity.ok(invitations);
    }

    @PostMapping("/{invitationId}/accept")
    public ResponseEntity<Void> acceptInvitation(
            @PathVariable UUID invitationId,
            Authentication authentication) {
        invitationService.acceptInvitation(invitationId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{invitationId}/decline")
    public ResponseEntity<Void> declineInvitation(
            @PathVariable UUID invitationId,
            Authentication authentication) {
        invitationService.declineInvitation(invitationId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
