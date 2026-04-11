package com.p2ps.telemetry.controller;

import com.p2ps.telemetry.model.TelemetryRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.p2ps.telemetry.dto.TelemetryPingDTO;
import com.p2ps.telemetry.services.TelemetryService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/telemetry")
@RequiredArgsConstructor
@Slf4j
public class TelemetryController {

    private final TelemetryService telemetryService;

    @PostMapping("/ping")
    public ResponseEntity<Map<String, String>> receivePing(@RequestBody TelemetryPingDTO pingDTO) {
        //Console log
        log.info("[API] Ping received for the product: {}", pingDTO.getItemId());

        //processing the ping
        telemetryService.processPing(pingDTO);

        //success response
        return ResponseEntity.accepted().body(Map.of("status", "success"));
    }

    @GetMapping("/pings")
    public ResponseEntity<List<TelemetryRecord>> getPings(
            @RequestParam String storeId,
            @RequestParam String itemId) {
        log.info("[API] GET pings for storeId: {}, itemId: {}", storeId, itemId);
        List<TelemetryRecord> records = telemetryService.getPings(storeId, itemId);
        return ResponseEntity.ok(records);
    }
}