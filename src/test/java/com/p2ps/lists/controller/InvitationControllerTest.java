package com.p2ps.lists.controller;

import com.p2ps.exception.GlobalExceptionHandler;
import com.p2ps.lists.dto.ListInvitationDTO;
import com.p2ps.lists.exception.InvitationNotFoundException;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.model.InvitationStatus;
import com.p2ps.lists.service.InvitationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InvitationControllerTest {

    @Mock
    private InvitationService invitationService;

    @InjectMocks
    private InvitationController invitationController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(invitationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getPendingInvitationsShouldReturnList() throws Exception {
        UUID invId = UUID.randomUUID();
        ListInvitationDTO dto = new ListInvitationDTO();
        dto.setId(invId);
        dto.setListTitle("Groceries");
        dto.setInviterName("Owner User");
        dto.setInviterEmail("o***@example.com");
        dto.setStatus(InvitationStatus.PENDING);
        dto.setCreatedAt(LocalDateTime.now());

        when(invitationService.getPendingInvitations("invitee@example.com"))
                .thenReturn(List.of(dto));

        mockMvc.perform(get("/api/invitations")
                        .principal(new UsernamePasswordAuthenticationToken("invitee@example.com", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(invId.toString()))
                .andExpect(jsonPath("$[0].listTitle").value("Groceries"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        verify(invitationService).getPendingInvitations("invitee@example.com");
    }

    @Test
    void getPendingInvitationsShouldReturnEmptyList() throws Exception {
        when(invitationService.getPendingInvitations("nobody@example.com"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/invitations")
                        .principal(new UsernamePasswordAuthenticationToken("nobody@example.com", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void acceptInvitationShouldReturnNoContent() throws Exception {
        UUID invId = UUID.randomUUID();

        mockMvc.perform(post("/api/invitations/{invitationId}/accept", invId)
                        .principal(new UsernamePasswordAuthenticationToken("invitee@example.com", null)))
                .andExpect(status().isNoContent());

        verify(invitationService).acceptInvitation(invId, "invitee@example.com");
    }

    @Test
    void acceptInvitationShouldReturnNotFoundWhenMissing() throws Exception {
        UUID invId = UUID.randomUUID();

        doThrow(new InvitationNotFoundException("Invitation not found"))
                .when(invitationService).acceptInvitation(invId, "invitee@example.com");

        mockMvc.perform(post("/api/invitations/{invitationId}/accept", invId)
                        .principal(new UsernamePasswordAuthenticationToken("invitee@example.com", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource Not Found"))
                .andExpect(jsonPath("$.details").value("Invitation not found"));
    }

    @Test
    void acceptInvitationShouldReturnForbiddenForWrongUser() throws Exception {
        UUID invId = UUID.randomUUID();

        doThrow(new ListAccessDeniedException("This invitation is not for you"))
                .when(invitationService).acceptInvitation(invId, "wrong@example.com");

        mockMvc.perform(post("/api/invitations/{invitationId}/accept", invId)
                        .principal(new UsernamePasswordAuthenticationToken("wrong@example.com", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Forbidden"));
    }

    @Test
    void declineInvitationShouldReturnNoContent() throws Exception {
        UUID invId = UUID.randomUUID();

        mockMvc.perform(post("/api/invitations/{invitationId}/decline", invId)
                        .principal(new UsernamePasswordAuthenticationToken("invitee@example.com", null)))
                .andExpect(status().isNoContent());

        verify(invitationService).declineInvitation(invId, "invitee@example.com");
    }

    @Test
    void declineInvitationShouldReturnNotFoundWhenMissing() throws Exception {
        UUID invId = UUID.randomUUID();

        doThrow(new InvitationNotFoundException("Invitation not found"))
                .when(invitationService).declineInvitation(invId, "invitee@example.com");

        mockMvc.perform(post("/api/invitations/{invitationId}/decline", invId)
                        .principal(new UsernamePasswordAuthenticationToken("invitee@example.com", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource Not Found"));
    }
}
