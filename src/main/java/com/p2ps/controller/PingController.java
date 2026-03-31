package com.p2ps.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

/**
 * Controller handling WebSocket ping-pong diagnostic checks.
 * Verifies the two-way data bridge between the frontend application and the server.
 */
@Controller
public class PingController {

    private static final Logger logger = LoggerFactory.getLogger(PingController.class);

    /**
     * Intercepts messages sent to the /app/ping destination.
     * Logs the incoming payload size securely and broadcasts a response to all subscribers.
     *
     * @param incomingMessage the raw string payload sent by the frontend client
     * @return the automated pong response string
     */
    @MessageMapping("/ping")
    @SendTo("/topic/pong")
    public String handlePing(String incomingMessage) {
        int payloadLength = incomingMessage == null ? 0 : incomingMessage.length();
        logger.debug("Received ping from frontend (payloadLength={})", payloadLength);
        return "Pong from Spring Boot! The two-way highway is open.";
    }
}