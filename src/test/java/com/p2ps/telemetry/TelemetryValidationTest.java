package com.p2ps.telemetry;

import com.p2ps.telemetry.dto.TelemetryPingDTO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryValidationTest {

    private Validator validator;
    private ValidatorFactory factory;

    @BeforeEach
    void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    void shouldPassValidationForValidDTO() {
        TelemetryPingDTO dto = new TelemetryPingDTO();
        dto.setDeviceId("device-1");
        dto.setStoreId("store-001");
        dto.setItemId("pasta");
        dto.setLat(44.4268);
        dto.setLng(26.1025);
        dto.setAccuracyMeters(3.5);
        dto.setTimestamp(1748123456789L);

        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void shouldFailValidationWhenLatIsOutOfRange() {
        TelemetryPingDTO dto = new TelemetryPingDTO();
        dto.setDeviceId("device-1");
        dto.setStoreId("store-001");
        dto.setItemId("pasta");
        dto.setLat(999.0);
        dto.setLng(26.1025);
        dto.setAccuracyMeters(3.5);
        dto.setTimestamp(1748123456789L);

        Set<ConstraintViolation<TelemetryPingDTO>> violations = validator.validate(dto);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("lat")), "Expected validation error on property 'lat'");
    }

    @Test
    void shouldFailValidationWhenLngIsOutOfRange() {
        TelemetryPingDTO dto = new TelemetryPingDTO();
        dto.setDeviceId("device-1");
        dto.setStoreId("store-001");
        dto.setItemId("pasta");
        dto.setLat(44.4268);
        dto.setLng(999.0);
        dto.setAccuracyMeters(3.5);
        dto.setTimestamp(1748123456789L);

        Set<ConstraintViolation<TelemetryPingDTO>> violations = validator.validate(dto);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("lng")), "Expected validation error on property 'lng'");
    }

    @Test
    void shouldFailValidationWhenDeviceIdIsBlank() {
        TelemetryPingDTO dto = new TelemetryPingDTO();
        dto.setDeviceId("");
        dto.setStoreId("store-001");
        dto.setItemId("pasta");
        dto.setLat(44.4268);
        dto.setLng(26.1025);
        dto.setAccuracyMeters(3.5);
        dto.setTimestamp(1748123456789L);

        Set<ConstraintViolation<TelemetryPingDTO>> violations = validator.validate(dto);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("deviceId")), "Expected validation error on property 'deviceId'");
    }

    @Test
    void shouldFailValidationWhenTimestampIsNegative() {
        TelemetryPingDTO dto = new TelemetryPingDTO();
        dto.setDeviceId("device-1");
        dto.setStoreId("store-001");
        dto.setItemId("pasta");
        dto.setLat(44.4268);
        dto.setLng(26.1025);
        dto.setAccuracyMeters(3.5);
        dto.setTimestamp(-1L);

        Set<ConstraintViolation<TelemetryPingDTO>> violations = validator.validate(dto);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("timestamp")), "Expected validation error on property 'timestamp'");
    }
}
