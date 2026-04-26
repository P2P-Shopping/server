package com.p2ps.service;

import com.p2ps.client.OsrmClient;
import com.p2ps.controller.MacroRoutingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MacroRoutingServiceTest {

    private JdbcTemplate jdbcTemplate;
    private OsrmClient osrmClient;
    private MacroRoutingService service;

    private static final String STORE_ID = "8f3e1a2b-c4d5-6e7f-8a9b-0c1d2e3f4a5b";

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        osrmClient = mock(OsrmClient.class);
        service = new MacroRoutingService(jdbcTemplate, osrmClient);
    }

    @Test
    void getEstimates_shouldReturnBothEstimatesWhenOsrmResponds() {
        mockStoreEntrance(47.156, 27.587);
        when(osrmClient.getEstimate(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("foot")))
                .thenReturn(new OsrmClient.TransportEstimate(850.0, 612.0));
        when(osrmClient.getEstimate(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("car")))
                .thenReturn(new OsrmClient.TransportEstimate(1200.0, 145.0));

        MacroRoutingResponse response = service.getEstimates(47.15, 27.58, STORE_ID);

        assertNotNull(response);
        assertNotNull(response.getWalking());
        assertNotNull(response.getDriving());
        assertEquals(850.0, response.getWalking().getDistanceM(), 0.001);
        assertEquals(612.0, response.getWalking().getDurationSeconds(), 0.001);
        assertEquals(1200.0, response.getDriving().getDistanceM(), 0.001);
        assertEquals(145.0, response.getDriving().getDurationSeconds(), 0.001);
    }

    @Test
    void getEstimates_shouldReturnNullWhenStoreNotFound() {
        when(jdbcTemplate.queryForList(anyString(), eq(STORE_ID))).thenReturn(List.of());

        MacroRoutingResponse response = service.getEstimates(47.15, 27.58, STORE_ID);

        assertNull(response);
        verifyNoInteractions(osrmClient);
    }

    @Test
    void getEstimates_shouldReturnNullWalkingWhenOsrmFailsForFoot() {
        mockStoreEntrance(47.156, 27.587);
        when(osrmClient.getEstimate(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("foot")))
                .thenReturn(null);
        when(osrmClient.getEstimate(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("car")))
                .thenReturn(new OsrmClient.TransportEstimate(1200.0, 145.0));

        MacroRoutingResponse response = service.getEstimates(47.15, 27.58, STORE_ID);

        assertNotNull(response);
        assertNull(response.getWalking());
        assertNotNull(response.getDriving());
    }

    @Test
    void getEstimates_shouldReturnNullDrivingWhenOsrmFailsForCar() {
        mockStoreEntrance(47.156, 27.587);
        when(osrmClient.getEstimate(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("foot")))
                .thenReturn(new OsrmClient.TransportEstimate(850.0, 612.0));
        when(osrmClient.getEstimate(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("car")))
                .thenReturn(null);

        MacroRoutingResponse response = service.getEstimates(47.15, 27.58, STORE_ID);

        assertNotNull(response);
        assertNotNull(response.getWalking());
        assertNull(response.getDriving());
    }

    @Test
    void getEstimates_shouldCallOsrmWithCorrectProfiles() {
        mockStoreEntrance(47.156, 27.587);
        when(osrmClient.getEstimate(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(null);

        service.getEstimates(47.15, 27.58, STORE_ID);

        verify(osrmClient).getEstimate(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("foot"));
        verify(osrmClient).getEstimate(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("car"));
    }

    @SuppressWarnings("unchecked")
    private void mockStoreEntrance(double lat, double lng) {
        Map<String, Object> row = Map.of("lat", lat, "lng", lng);
        when(jdbcTemplate.queryForList(anyString(), eq(STORE_ID))).thenReturn(List.of(row));
    }
}
