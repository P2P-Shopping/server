package com.p2ps.controller;

import com.p2ps.dto.ListUpdatePayload;
import com.p2ps.sync.service.ListSyncRouterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * WebSocket controller responsible for routing list-specific synchronization messages.
 * Acts as the traffic director for isolated "Rooms" based on List IDs.
 */
@Controller
public class ListSyncController {

    private static final Logger logger = LoggerFactory.getLogger(ListSyncController.class);

    private final ListSyncRouterService listSyncRouterService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public ListSyncController(ListSyncRouterService listSyncRouterService, SimpMessagingTemplate messagingTemplate) {
        this.listSyncRouterService = listSyncRouterService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Intercepts updates sent to a specific shopping list and broadcasts them to that list's room.
     * @param listId  the unique identifier of the shopping list, extracted from the destination path
     * @param payload the data detailing the modification made to the list
     * @return the exact payload to be broadcasted to all active subscribers of the room
     */
    @MessageMapping("/list/{listId}/update")
    @SendTo("/topic/list/{listId}")
    public ListUpdatePayload handleListUpdate(@DestinationVariable String listId, ListUpdatePayload payload) {
        if (payload == null) {
            logger.warn("Received null payload for list update on room");
            throw new IllegalArgumentException("Payload must not be null. Error thrown for: " + listId);
        }

        logger.debug("Routing action {} for room {}", payload.getAction(), listId);
        return listSyncRouterService.route(listId, payload);
    }

    /**
     * Processes a flood of updates (e.g. from a user coming back online) and broadcasts results individually.
     * @param listId   the unique identifier of the shopping list
     * @param payloads the batch of modifications
     */
    @MessageMapping("/list/{listId}/batch-update")
    public void handleBatchUpdate(@DestinationVariable String listId, List<ListUpdatePayload> payloads) {
        if (listId == null || listId.isBlank()) {
            logger.error("Received batch update with blank listId");
            throw new IllegalArgumentException("listId must not be blank");
        }

        if (payloads == null || payloads.isEmpty()) {
            logger.warn("Received null or empty payload list for batch update");
            return;
        }

        logger.info("Received batch update of size {}", payloads.size());
        List<ListUpdatePayload> results = listSyncRouterService.routeBatch(listId, payloads);
        
        for (ListUpdatePayload result : results) {
            try {
                messagingTemplate.convertAndSend("/topic/list/" + listId, result);
            } catch (Exception e) {
                logger.error("Failed to broadcast update for list {}: {}", listId, e.getMessage());
            }
        }
    }
}

