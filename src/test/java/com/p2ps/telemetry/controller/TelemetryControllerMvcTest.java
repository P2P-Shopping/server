package com.p2ps.telemetry.controller;

import com.p2ps.exception.GlobalExceptionHandler;
import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import com.p2ps.telemetry.services.TelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @BeforeEach
    void setUp() {
        telemetryService = mock(TelemetryService.class);
        LocationProcessorWorker locationProcessorWorker = mock(LocationProcessorWorker.class);
        StoreInventoryMapRepository mapRepository = mock(StoreInventoryMapRepository.class);

        TelemetryController controller = new TelemetryController(
                telemetryService,
                locationProcessorWorker,
                mapRepository,
                mock(JdbcTemplate.class)
        );

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturn400WhenPayloadIsInvalid() throws Exception {
        String invalidJson = "{\"storeId\": \"store-1\", \"itemId\": \"item-1\"}";

        mockMvc.perform(post("/api/v1/telemetry/ping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Validation Error")));

        verify(telemetryService, times(0)).processPing(any());
    }
}