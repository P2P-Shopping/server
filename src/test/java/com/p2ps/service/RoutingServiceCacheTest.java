package com.p2ps.service;

import com.p2ps.controller.RoutingRequest;
import com.p2ps.controller.RoutingResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingServiceCacheTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void calculateOptimalRoute_shouldReturnErrorWhenUserNotInStore() {
        RouteOptimizer optimizer = new RouteOptimizer();
        RoutingAsyncService asyncService = mock(RoutingAsyncService.class);
        RoutingService service = new RoutingService(jdbcTemplate, optimizer, asyncService);

        // No store found for user coordinates
        when(jdbcTemplate.queryForList(anyString(), any(Class.class), any(), any()))
                .thenReturn(List.of());

        RoutingRequest request = new RoutingRequest();
        request.setUserLat(0.0);
        request.setUserLng(0.0);
        request.setProductIds(List.of("item_101"));

        RoutingResponse response = service.calculateOptimalRoute(request);

        assertNotNull(response);
        assertEquals("error", response.getStatus());
        assertNotNull(response.getWarnings());
        assertFalse(response.getWarnings().isEmpty());
    }
}
