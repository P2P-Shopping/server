package p2ps.telemetry.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import p2ps.telemetry.dto.TelemetryPingDTO;
import p2ps.telemetry.services.TelemetryService;

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
}