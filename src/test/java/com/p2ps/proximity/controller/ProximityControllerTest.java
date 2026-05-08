package com.p2ps.proximity.controller;

import com.p2ps.proximity.service.ProximityMatchingService;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ProximityControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private ProximityMatchingService proximityMatchingService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void shouldReturn202WhenPingIsValid() throws Exception {
        doNothing().when(proximityMatchingService).processLocationPing(any());

        mockMvc.perform(post("/api/v1/proximity/ping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "deviceId": "device-001",
                                "lat": 47.15,
                                "lng": 27.59,
                                "timestamp": 1234567890000,
                                "fcmToken": "fcm-token-abc"
                            }
                            """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"));

        // Verify service was actually called
        verify(proximityMatchingService).processLocationPing(any());
    }
    @Test
    void shouldReturn400WhenDeviceIdIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/proximity/ping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "lat": 47.15,
                                    "lng": 27.59,
                                    "timestamp": 1234567890000,
                                    "fcmToken": "fcm-token-abc"
                                }
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(proximityMatchingService);
    }

    @Test
    void shouldReturn400WhenFcmTokenIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/proximity/ping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "deviceId": "device-001",
                                    "lat": 47.15,
                                    "lng": 27.59,
                                    "timestamp": 1234567890000
                                }
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(proximityMatchingService);
    }

    @Test
    void shouldReturn400WhenLatitudeIsOutOfRange() throws Exception {
        mockMvc.perform(post("/api/v1/proximity/ping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "deviceId": "device-001",
                                    "lat": 999.0,
                                    "lng": 27.59,
                                    "timestamp": 1234567890000,
                                    "fcmToken": "fcm-token-abc"
                                }
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(proximityMatchingService);
    }
}