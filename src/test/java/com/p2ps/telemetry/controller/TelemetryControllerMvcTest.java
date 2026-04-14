package com.p2ps.telemetry.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.telemetry.dto.TelemetryPingDTO;
import com.p2ps.telemetry.services.TelemetryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TelemetryController.class)
class TelemetryControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TelemetryService telemetryService;

    @Test
    void shouldReturn400WhenPayloadIsInvalid() throws Exception {
        // Build a JSON payload missing required fields (deviceId and lat/lng)
        String invalidJson = "{\"storeId\": \"store-1\", \"itemId\": \"item-1\"}";

        mockMvc.perform(post("/api/v1/telemetry/ping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Validation Error")));

        // Ensure controller's service is not invoked when validation fails
        verify(telemetryService, times(0)).processPing((TelemetryPingDTO) org.mockito.Mockito.any());
    }
}
