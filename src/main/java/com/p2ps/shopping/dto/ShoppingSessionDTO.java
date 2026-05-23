package com.p2ps.shopping.dto;

import com.p2ps.shopping.model.ShoppingSessionStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ShoppingSessionDTO {
    private UUID sessionId;
    private UUID listId;
    private ShoppingSessionStatus status;
    private UUID storeId;
    private UUID storeCandidateSubmissionId;
    private String storeName;
    private String storeAddress;
    private boolean officialStore;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
