package com.p2ps.lists.dto;

import com.p2ps.lists.model.InvitationStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ListInvitationDTO {
    private UUID id;
    private UUID listId;
    private String listTitle;
    private String inviterName;
    private String inviterEmail;
    private InvitationStatus status;
    private LocalDateTime createdAt;
}
