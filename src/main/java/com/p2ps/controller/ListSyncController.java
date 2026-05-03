package com.p2ps.controller;

import com.p2ps.dto.ListUpdatePayload;
import com.p2ps.sync.service.ListSyncRouterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

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
    public void handleListUpdate(@DestinationVariable String listId, ListUpdatePayload payload) {
        if (payload == null) {
            logger.warn("Received null payload for list update on room");
            return;
        }

        logger.info("Routing action {} for room {}", payload.getAction(), listId);
        try {
            logger.info("RECEIVED update for list: {} | Action: {} | Item: {}", listId, payload.getAction(), payload.getItemId());
            ListUpdatePayload processedPayload = listSyncRouterService.route(listId, payload);
            if (processedPayload != null) {
                String destination = "/topic/list/" + listId;
                logger.info("BROADCASTING update to destination: {} | Status: {}", destination, processedPayload.getStatus());
                messagingTemplate.convertAndSend(destination, processedPayload);
            } else {
                logger.warn("Processed payload was null for list: {}, nothing to broadcast", listId);
            }
        } catch (Exception e) {
            logger.error("CRITICAL: Error processing list update for list {}: {}", listId, e.getMessage(), e);
        }
    }
}
