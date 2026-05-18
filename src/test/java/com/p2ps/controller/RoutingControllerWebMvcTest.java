package com.p2ps.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import com.p2ps.service.MacroRoutingService;
import com.p2ps.service.RoutingService;
import com.p2ps.service.StoreMatchingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoutingControllerWebMvcTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private RoutingService routingService;
    private MacroRoutingService macroRoutingService;
    private StoreInventoryMapRepository inventoryMapRepository;
    private LocationProcessorWorker locationProcessorWorker;
    private StoreMatchingEngine storeMatchingEngine;
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        routingService = mock(RoutingService.class);
        macroRoutingService = mock(MacroRoutingService.class);
        inventoryMapRepository = mock(StoreInventoryMapRepository.class);
        locationProcessorWorker = mock(LocationProcessorWorker.class);
        storeMatchingEngine = mock(StoreMatchingEngine.class);
        redis = mock(StringRedisTemplate.class);
        objectMapper = new ObjectMapper();

        RoutingController controller = new RoutingController(
                routingService,
                macroRoutingService,
                inventoryMapRepository,
                locationProcessorWorker,
                storeMatchingEngine,
                redis,
                objectMapper
        );
        ReflectionTestUtils.setField(controller, "recalculationCooldown", Duration.ofMinutes(1));
        ReflectionTestUtils.setField(controller, "recalculationGuardMaxSize", 10000);
        controller.init();

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void storesMatch_shouldReturn200AndEmptyListWhenNoStoresMatch() throws Exception {
        when(storeMatchingEngine.findOptimalStores(eq(47.15), eq(27.58), eq(5000.0), any(), any()))
                .thenReturn(List.of());

        StoreMatchRequest request = new StoreMatchRequest();
        request.setUserLat(47.15);
        request.setUserLng(27.58);
        request.setRadiusInMeters(5000);
        request.setItemIds(List.of(UUID.randomUUID()));
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/routing/stores-match")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void storesMatch_shouldReturnCompactStorePayload() throws Exception {
        UUID firstItemId = UUID.randomUUID();
        UUID secondItemId = UUID.randomUUID();
        String storeId = UUID.randomUUID().toString();

        when(storeMatchingEngine.findOptimalStores(eq(47.15), eq(27.58), eq(5000.0), any(), any()))
                .thenReturn(List.of(new StoreMatchingEngine.StoreMatchResult(storeId, "Store A", 1, 800.0)));

        StoreMatchRequest request = new StoreMatchRequest();
        request.setUserLat(47.15);
        request.setUserLng(27.58);
        request.setRadiusInMeters(5000);
        request.setItemIds(List.of(firstItemId, secondItemId));

        mockMvc.perform(post("/api/routing/stores-match")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].storeId").value(storeId))
                .andExpect(jsonPath("$[0].matchPercentage").value(50));
    }
}


