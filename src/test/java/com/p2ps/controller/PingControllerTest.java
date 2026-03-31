package com.p2ps.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PingControllerTest {

    @Test
    void shouldReturnPongMessageWhenIncomingMessageIsProvided() {
        PingController controller = new PingController();

        String response = controller.handlePing("hello from frontend");

        assertEquals("Pong from Spring Boot! The two-way highway is open.", response);
    }

    @Test
    void shouldReturnPongMessageWhenIncomingMessageIsNull() {
        PingController controller = new PingController();

        String response = controller.handlePing(null);

        assertEquals("Pong from Spring Boot! The two-way highway is open.", response);
    }
}
