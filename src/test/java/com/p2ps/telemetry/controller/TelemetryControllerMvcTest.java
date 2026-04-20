package com.p2ps.telemetry.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.exception.GlobalExceptionHandler;
import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import com.p2ps.telemetry.services.TelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TelemetryControllerMvcTest {

    private MockMvc mockMvc;
    private TelemetryService telemetryService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        telemetryService = mock(TelemetryService.class);
        LocationProcessorWorker locationProcessorWorker = mock(LocationProcessorWorker.class);
        StoreInventoryMapRepository mapRepository = mock(StoreInventoryMapRepository.class);

        // Instantiate controller with all required dependencies
        TelemetryController controller = new TelemetryController(
                telemetryService,
                locationProcessorWorker,
                mapRepository
        );

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
    }

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
        verify(telemetryService, times(0)).processPing(any());
    }
}
