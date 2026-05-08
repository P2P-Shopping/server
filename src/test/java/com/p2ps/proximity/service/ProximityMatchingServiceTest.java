package com.p2ps.proximity.service;

import com.p2ps.proximity.dto.LocationPingDTO;
import com.p2ps.proximity.model.ActiveListLocation;
import com.p2ps.proximity.repository.ActiveListLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProximityMatchingServiceTest {

    @Mock
    private ActiveListLocationRepository activeListLocationRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private FcmService fcmService;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ProximityMatchingService proximityMatchingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(proximityMatchingService, "radiusMeters", 500.0);
        ReflectionTestUtils.setField(proximityMatchingService, "debounceHours", 24L);
        ReflectionTestUtils.setField(proximityMatchingService, "appBaseUrl", "http://localhost:5173");
    }

    private LocationPingDTO buildPing() {
        LocationPingDTO dto = new LocationPingDTO();
        dto.setDeviceId("device-001");
        dto.setLat(47.15);
        dto.setLng(27.59);
        dto.setTimestamp(System.currentTimeMillis());
        dto.setFcmToken("fcm-token-abc");
        return dto;
    }

    private ActiveListLocation buildLocation(String listId) {
        ActiveListLocation loc = new ActiveListLocation();
        loc.setId("loc-" + listId);
        loc.setListId(listId);
        loc.setItemId("item-001");
        loc.setOwnerEmail("owner@example.com");
        loc.setCoordinates(new double[]{27.59, 47.15});
        return loc;
    }

    @Test
    void shouldSendNotificationWhenNearbyListFoundAndNotDebounced() {
        LocationPingDTO ping = buildPing();
        ActiveListLocation nearby = buildLocation("list-001");

        when(activeListLocationRepository.findByCoordinatesNear(any(Point.class), any(Distance.class)))
                .thenReturn(List.of(nearby));
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        proximityMatchingService.processLocationPing(ping);

        verify(fcmService).sendProximityAlert(
                eq("fcm-token-abc"),
                eq("Item nearby!"),
                anyString(),
                contains("list-001")
        );
        verify(valueOperations).set(anyString(), eq("1"), eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    void shouldSkipNotificationWhenDebounceKeyExists() {
        LocationPingDTO ping = buildPing();
        ActiveListLocation nearby = buildLocation("list-001");

        when(activeListLocationRepository.findByCoordinatesNear(any(Point.class), any(Distance.class)))
                .thenReturn(List.of(nearby));
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        proximityMatchingService.processLocationPing(ping);

        verify(fcmService, never()).sendProximityAlert(any(), any(), any(), any());
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void shouldDoNothingWhenNoNearbyListsFound() {
        LocationPingDTO ping = buildPing();

        when(activeListLocationRepository.findByCoordinatesNear(any(Point.class), any(Distance.class)))
                .thenReturn(Collections.emptyList());

        proximityMatchingService.processLocationPing(ping);

        verify(fcmService, never()).sendProximityAlert(any(), any(), any(), any());
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    void shouldSendNotificationForEachNearbyListNotDebounced() {
        LocationPingDTO ping = buildPing();
        ActiveListLocation loc1 = buildLocation("list-001");
        ActiveListLocation loc2 = buildLocation("list-002");

        when(activeListLocationRepository.findByCoordinatesNear(any(Point.class), any(Distance.class)))
                .thenReturn(List.of(loc1, loc2));
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        proximityMatchingService.processLocationPing(ping);

        verify(fcmService, times(2)).sendProximityAlert(any(), any(), any(), any());
        verify(valueOperations, times(2)).set(anyString(), eq("1"), eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    void shouldOnlySendForNonDebouncedListsWhenMixed() {
        LocationPingDTO ping = buildPing();
        ActiveListLocation loc1 = buildLocation("list-001");
        ActiveListLocation loc2 = buildLocation("list-002");

        when(activeListLocationRepository.findByCoordinatesNear(any(Point.class), any(Distance.class)))
                .thenReturn(List.of(loc1, loc2));

        String debounceKey1 = "proximity:notified:device-001:list-001";
        String debounceKey2 = "proximity:notified:device-001:list-002";
        when(redisTemplate.hasKey(debounceKey1)).thenReturn(true);
        when(redisTemplate.hasKey(debounceKey2)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        proximityMatchingService.processLocationPing(ping);

        ArgumentCaptor<String> deepLinkCaptor = ArgumentCaptor.forClass(String.class);
        verify(fcmService, times(1)).sendProximityAlert(any(), any(), any(), deepLinkCaptor.capture());
        assert deepLinkCaptor.getValue().contains("list-002");
        verify(valueOperations, times(1)).set(anyString(), eq("1"), eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    void shouldBuildCorrectDeepLinkUrl() {
        LocationPingDTO ping = buildPing();
        ActiveListLocation nearby = buildLocation("list-xyz");

        when(activeListLocationRepository.findByCoordinatesNear(any(Point.class), any(Distance.class)))
                .thenReturn(List.of(nearby));
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        proximityMatchingService.processLocationPing(ping);

        ArgumentCaptor<String> deepLinkCaptor = ArgumentCaptor.forClass(String.class);
        verify(fcmService).sendProximityAlert(any(), any(), any(), deepLinkCaptor.capture());
        assert deepLinkCaptor.getValue().equals("http://localhost:5173/list/list-xyz");
    }
}
