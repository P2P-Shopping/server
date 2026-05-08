package com.p2ps.telemetry.services;

import com.p2ps.telemetry.model.PingStatus;
import com.p2ps.telemetry.model.TelemetryRecord;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryRawPingImportJobTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    private TelemetryRawPingImportJob importJob;

    @BeforeEach
    void setUp() {
        importJob = new TelemetryRawPingImportJob(mongoTemplate, jdbcTemplate, dataSource);
        ReflectionTestUtils.setField(importJob, "schedulingEnabled", true);
        ReflectionTestUtils.setField(importJob, "importEnabled", true);
        ReflectionTestUtils.setField(importJob, "batchSize", 500);
        ReflectionTestUtils.setField(importJob, "autoProvisionDimensions", true);
        ReflectionTestUtils.setField(importJob, "postgresDetected", true);
    }

    @Test
    void shouldImportOnlyAcceptedTelemetryRecords() {
        TelemetryRecord accepted = record(PingStatus.ACCEPTED);
        TelemetryRecord degraded = record(PingStatus.DEGRADED);
        TelemetryRecord rejected = record(PingStatus.REJECTED);

        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("telemetry_raw_ping_import")))
                .thenReturn(List.of());
        when(mongoTemplate.find(any(), eq(TelemetryRecord.class)))
                .thenReturn(List.of(degraded, accepted, rejected));

        importJob.importNewTelemetryRecords();

        verify(jdbcTemplate).update(
                contains("INSERT INTO raw_user_pings"),
                eq(accepted.getId()),
                eq(accepted.getStoreId()),
                eq(accepted.getItemId()),
                eq(accepted.getStoreId()),
                eq(accepted.getItemId()),
                eq(accepted.getLng()),
                eq(accepted.getLat()),
                eq(accepted.getAccuracyMeters()),
                eq("GPS"),
                any()
        );
        verify(jdbcTemplate).update(
                contains("INSERT INTO store_geofences"),
                eq(accepted.getStoreId()),
                eq(accepted.getStoreId()),
                eq("Imported Store " + accepted.getStoreId()),
                eq(accepted.getLng()),
                eq(accepted.getLat()),
                eq(accepted.getLng()),
                eq(accepted.getLat()),
                eq(accepted.getLng()),
                eq(accepted.getLat())
        );
        verify(jdbcTemplate).update(
                contains("INSERT INTO items"),
                eq(accepted.getItemId()),
                eq(accepted.getItemId()),
                eq("Imported Item " + accepted.getItemId()),
                eq(accepted.getTimestamp()),
                eq(accepted.getTimestamp()),
                eq("00000000-0000-0000-0000-000000000001")
        );
        verify(jdbcTemplate).update(
                contains("INSERT INTO telemetry_import_state"),
                eq("telemetry_raw_ping_import"),
                eq(rejected.getId())
        );
    }

    @Test
    void shouldNotReadMongoWhenImportIsDisabled() {
        ReflectionTestUtils.setField(importJob, "importEnabled", false);

        importJob.importNewTelemetryRecords();

        verify(mongoTemplate, never()).find(any(), eq(TelemetryRecord.class));
    }

    @Test
    void requiredTelemetrySchemaPresent_shouldReturnTrueWhenAllTablesExist() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("store_geofences"))).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("items"))).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("users"))).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("shopping_lists"))).thenReturn(true);

        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "requiredTelemetrySchemaPresent");

        assert result != null && result;
        verify(jdbcTemplate, times(4)).queryForObject(anyString(), eq(Boolean.class), anyString());
    }

    @Test
    void requiredTelemetrySchemaPresent_shouldReturnFalseWhenOneTableMissing() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("store_geofences"))).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("items"))).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("users"))).thenReturn(false);

        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "requiredTelemetrySchemaPresent");

        assert result != null && !result;
    }

    @Test
    void tableExists_shouldReturnFalseOnException() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), anyString()))
                .thenThrow(new RuntimeException("Connection error"));

        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "tableExists", "some_table");

        assert result != null && !result;
    }

    @Test
    void tableExists_shouldReturnTrueWhenTableExists() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("items"))).thenReturn(true);

        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "tableExists", "items");

        assert result != null && result;
    }

    @Test
    void tableExists_shouldReturnFalseWhenTableDoesNotExist() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("nonexistent"))).thenReturn(false);

        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "tableExists", "nonexistent");

        assert result != null && !result;
    }

    @Test
    void initialize_shouldSkipSchemaSetupWhenSchemaNotPresent() {
        TelemetryRawPingImportJob newJob = new TelemetryRawPingImportJob(mongoTemplate, jdbcTemplate, dataSource);
        ReflectionTestUtils.setField(newJob, "schedulingEnabled", true);
        ReflectionTestUtils.setField(newJob, "importEnabled", true);
        ReflectionTestUtils.setField(newJob, "batchSize", 500);
        ReflectionTestUtils.setField(newJob, "autoProvisionDimensions", true);
        ReflectionTestUtils.setField(newJob, "postgresDetected", true);

        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("store_geofences"))).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("items"))).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("users"))).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("shopping_lists"))).thenReturn(false);

        newJob.initialize();

        verify(jdbcTemplate, never()).execute(contains("CREATE TABLE IF NOT EXISTS telemetry_import_state"));
    }

    private TelemetryRecord record(PingStatus status) {
        TelemetryRecord record = new TelemetryRecord();
        record.setId(new ObjectId().toHexString());
        record.setDeviceId("device-1");
        record.setStoreId(UUID.randomUUID().toString());
        record.setItemId(UUID.randomUUID().toString());
        record.setLat(44.4268);
        record.setLng(26.1025);
        record.setAccuracyMeters(4.5);
        record.setTimestamp(System.currentTimeMillis());
        record.setStatus(status);
        return record;
    }
}
