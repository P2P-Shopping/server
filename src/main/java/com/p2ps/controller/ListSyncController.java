package com.p2ps.controller;

import com.p2ps.dto.ListUpdatePayload;
import com.p2ps.sync.service.ListSyncRouterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

/**
 * WebSocket controller responsible for routing list-specific synchronization messages.
 * Acts as the traffic director for isolated "Rooms" based on List IDs.
 */
@Controller
public class ListSyncController {

    private static final Logger logger = LoggerFactory.getLogger(ListSyncController.class);

    private final ListSyncRouterService listSyncRouterService;

    @Autowired
    public ListSyncController(ListSyncRouterService listSyncRouterService) {
        this.listSyncRouterService = listSyncRouterService;
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

        logger.debug("Routing action for room");
        return listSyncRouterService.route(listId, payload);
    }
}
