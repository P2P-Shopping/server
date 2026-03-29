package p2ps.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class PingController {

    private static final Logger logger = LoggerFactory.getLogger(PingController.class);

    // 1. Listens for messages sent by the frontend to "/app/ping"
    @MessageMapping("/ping")

    // 2. Broadcasts the return value to anyone subscribed to "/topic/pong"
    @SendTo("/topic/pong")
    public String handlePing(String incomingMessage) {

        // 3. Logs the frontend's message to your IntelliJ console
        logger.info("Received ping from frontend: {}", incomingMessage);

        // 4. Returns the payload that gets broadcasted
        return "Pong from Spring Boot! The two-way highway is open.";
    }
}