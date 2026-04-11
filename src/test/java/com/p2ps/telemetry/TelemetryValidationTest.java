package com.p2ps.telemetry;

import com.p2ps.telemetry.dto.TelemetryPingDTO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
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

        assertFalse(validator.validate(dto).isEmpty());
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

        assertFalse(validator.validate(dto).isEmpty());
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

        assertFalse(validator.validate(dto).isEmpty());
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

        assertFalse(validator.validate(dto).isEmpty());
    }
}