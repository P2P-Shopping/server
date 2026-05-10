package com.p2ps.proximity.controller;

import com.p2ps.proximity.service.ProximityMatchingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
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

        verify(proximityMatchingService).processLocationPing(any());
    }

    static Stream<Arguments> invalidPingPayloads() {
        return Stream.of(
                Arguments.of("missing deviceId", """
                        {
                            "lat": 47.15,
                            "lng": 27.59,
                            "timestamp": 1234567890000,
                            "fcmToken": "fcm-token-abc"
                        }
                        """),
                Arguments.of("missing fcmToken", """
                        {
                            "deviceId": "device-001",
                            "lat": 47.15,
                            "lng": 27.59,
                            "timestamp": 1234567890000
                        }
                        """),
                Arguments.of("latitude out of range", """
                        {
                            "deviceId": "device-001",
                            "lat": 999.0,
                            "lng": 27.59,
                            "timestamp": 1234567890000,
                            "fcmToken": "fcm-token-abc"
                        }
                        """)
        );
    }

    @ParameterizedTest(name = "should return 400 when {0}")
    @MethodSource("invalidPingPayloads")
    void shouldReturn400ForInvalidPayloads(String description, String payload) throws Exception {
        mockMvc.perform(post("/api/v1/proximity/ping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(proximityMatchingService);
    }
}