package com.p2ps.proximity.controller;

import com.p2ps.proximity.dto.LocationPingDTO;
import com.p2ps.proximity.service.ProximityMatchingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for receiving background location pings from the Android app.
 * The endpoint is permit-all (same as telemetry) since it comes from hardware devices.
 */
@RestController
@RequestMapping("/api/v1/proximity")
@RequiredArgsConstructor
@Slf4j
public class ProximityController {

    private final ProximityMatchingService proximityMatchingService;

    /**
     * Receives a background location ping and triggers async proximity matching.
     * Returns 202 Accepted immediately — processing happens asynchronously.
     */
    @PostMapping("/ping")
    public ResponseEntity<Map<String, String>> receiveLocationPing(
            @Valid @RequestBody LocationPingDTO pingDTO) {
        log.info("[API] Background location ping received from device: {}", pingDTO.getDeviceId());
        proximityMatchingService.processLocationPing(pingDTO);
        return ResponseEntity.accepted().body(Map.of("status", "accepted"));
    }
}