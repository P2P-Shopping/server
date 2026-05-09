package com.p2ps.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OsrmClientTest {

    private RestTemplate restTemplate;
    private OsrmClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new OsrmClient(restTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(client, "osrmBaseUrl", "http://mock-osrm");
    }

    @Test
    void getEstimate_shouldReturnEstimateWithPolylineOnOkResponse() {
        String json = """
            {
              "code": "Ok",
              "routes": [{
                "distance": 850.5,
                "duration": 612.3,
                "geometry": "abcdefghij_polyline"
              }]
            }
            """;
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        OsrmClient.TransportEstimate result = client.getEstimate(47.15, 27.58, 47.16, 27.59, "foot");

        assertNotNull(result);
        assertEquals(850.5, result.distanceM(), 0.01);
        assertEquals(612.3, result.durationSeconds(), 0.01);
        assertEquals("abcdefghij_polyline", result.polyline());
    }

    @Test
    void getEstimate_shouldReturnNullPolylineWhenGeometryMissing() {
        String json = """
            {
              "code": "Ok",
              "routes": [{ "distance": 850.5, "duration": 612.3 }]
            }
            """;
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        OsrmClient.TransportEstimate result = client.getEstimate(47.15, 27.58, 47.16, 27.59, "foot");

        assertNotNull(result);
        assertNull(result.polyline());
    }

    @Test
    void getEstimate_shouldReturnNullWhenOsrmCodeIsNotOk() {
        String json = "{\"code\": \"NoRoute\", \"routes\": []}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        OsrmClient.TransportEstimate result = client.getEstimate(47.15, 27.58, 47.16, 27.59, "car");

        assertNull(result);
    }

    @Test
    void getEstimate_shouldReturnNullWhenRoutesArrayIsEmpty() {
        String json = "{\"code\": \"Ok\", \"routes\": []}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        OsrmClient.TransportEstimate result = client.getEstimate(47.15, 27.58, 47.16, 27.59, "foot");

        assertNull(result);
    }

    @Test
    void getEstimate_shouldReturnNullWhenRestTemplateThrows() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        OsrmClient.TransportEstimate result = client.getEstimate(47.15, 27.58, 47.16, 27.59, "car");

        assertNull(result);
    }

    @Test
    void getEstimate_shouldReturnNullWhenResponseIsInvalidJson() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("not json");

        OsrmClient.TransportEstimate result = client.getEstimate(47.15, 27.58, 47.16, 27.59, "foot");

        assertNull(result);
    }

    @Test
    void getEstimate_shouldUseFullOverviewInUrl() {
        String json = """
            {
              "code": "Ok",
              "routes": [{ "distance": 100.0, "duration": 60.0, "geometry": "xyz" }]
            }
            """;
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        client.getEstimate(47.15, 27.58, 47.16, 27.59, "foot");

       verify(restTemplate).getForObject(
        argThat(url -> url.toString().contains("overview=full") && url.toString().contains("geometries=polyline")),
        eq(String.class)
);
    }

    @Test
    void transportEstimate_recordShouldStoreAllValues() {
        OsrmClient.TransportEstimate estimate = new OsrmClient.TransportEstimate(100.0, 60.0, "test_polyline");
        assertEquals(100.0, estimate.distanceM(), 0.001);
        assertEquals(60.0, estimate.durationSeconds(), 0.001);
        assertEquals("test_polyline", estimate.polyline());
    }
}
