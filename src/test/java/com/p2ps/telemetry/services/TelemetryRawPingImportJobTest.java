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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private MongoTemplate fallbackMongoTemplate;

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
        TelemetryRecord accepted = createTelemetryRecord(PingStatus.ACCEPTED);
        TelemetryRecord degraded = createTelemetryRecord(PingStatus.DEGRADED);
        TelemetryRecord rejected = createTelemetryRecord(PingStatus.REJECTED);

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

        assertTrue(result != null && result);
        verify(jdbcTemplate, times(4)).queryForObject(anyString(), eq(Boolean.class), anyString());
    }

    @Test
    void requiredTelemetrySchemaPresent_shouldReturnFalseWhenOneTableMissing() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("store_geofences"))).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("items"))).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("users"))).thenReturn(false);

        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "requiredTelemetrySchemaPresent");

        assertTrue(result != null && !result);
    }

    @Test
    void tableExists_shouldReturnFalseOnException() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), anyString()))
                .thenThrow(new RuntimeException("Connection error"));

        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "tableExists", "some_table");

        assertTrue(result != null && !result);
    }

    @Test
    void tableExists_shouldReturnTrueWhenTableExists() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("items"))).thenReturn(true);

        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "tableExists", "items");

        assertTrue(result != null && result);
    }

    @Test
    void tableExists_shouldReturnFalseWhenTableDoesNotExist() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("nonexistent"))).thenReturn(false);

        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "tableExists", "nonexistent");

        assertTrue(result != null && !result);
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

    @Test
    void toInternalUuid_shouldReturnSameValueForUuid() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        String result = (String) ReflectionTestUtils.invokeMethod(importJob, "toInternalUuid", "store", uuid);
        assertThat(result).isEqualTo(uuid);
    }

    @Test
    void toInternalUuid_shouldGenerateDeterministicUuidForNonUuid() {
        String value = "my-store-id";
        String result1 = (String) ReflectionTestUtils.invokeMethod(importJob, "toInternalUuid", "store", value);
        String result2 = (String) ReflectionTestUtils.invokeMethod(importJob, "toInternalUuid", "store", value);
        assertThat(result1).isEqualTo(result2).isNotEqualTo(value);
    }

    @Test
    void toInternalUuid_shouldGenerateDifferentUuidsForDifferentNamespaces() {
        String value = "same-value";
        String result1 = (String) ReflectionTestUtils.invokeMethod(importJob, "toInternalUuid", "store", value);
        String result2 = (String) ReflectionTestUtils.invokeMethod(importJob, "toInternalUuid", "item", value);
        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    void isImportable_shouldReturnTrueForValidRecord() {
        TelemetryRecord telemetryRecord = createTelemetryRecord(PingStatus.ACCEPTED);
        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "isImportable", telemetryRecord);
        assertThat(result).isTrue();
    }

    @Test
    void isImportable_shouldReturnFalseForNullId() {
        TelemetryRecord telemetryRecord = createTelemetryRecord(PingStatus.ACCEPTED);
        telemetryRecord.setId(null);
        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "isImportable", telemetryRecord);
        assertThat(result).isFalse();
    }

    @Test
    void isImportable_shouldReturnFalseForDegradedStatus() {
        TelemetryRecord telemetryRecord = createTelemetryRecord(PingStatus.DEGRADED);
        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "isImportable", telemetryRecord);
        assertThat(result).isFalse();
    }

    @Test
    void isImportable_shouldAcceptNullLat_withStoreCentroidFallback() {
        TelemetryRecord telemetryRecord = createTelemetryRecord(PingStatus.ACCEPTED);
        telemetryRecord.setLat(null);
        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "isImportable", telemetryRecord);
        assertThat(result).isTrue();
    }

    @Test
    void isImportable_shouldAcceptNullLng_withStoreCentroidFallback() {
        TelemetryRecord telemetryRecord = createTelemetryRecord(PingStatus.ACCEPTED);
        telemetryRecord.setLng(null);
        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "isImportable", telemetryRecord);
        assertThat(result).isTrue();
    }

    @Test
    void isImportable_shouldReturnFalseForNullStoreId() {
        TelemetryRecord telemetryRecord = createTelemetryRecord(PingStatus.ACCEPTED);
        telemetryRecord.setStoreId(null);
        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "isImportable", telemetryRecord);
        assertThat(result).isFalse();
    }

    @Test
    void isImportable_shouldReturnFalseForBlankStoreId() {
        TelemetryRecord telemetryRecord = createTelemetryRecord(PingStatus.ACCEPTED);
        telemetryRecord.setStoreId("   ");
        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "isImportable", telemetryRecord);
        assertThat(result).isFalse();
    }

    @Test
    void isImportable_shouldReturnFalseForNullItemId() {
        TelemetryRecord telemetryRecord = createTelemetryRecord(PingStatus.ACCEPTED);
        telemetryRecord.setItemId(null);
        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "isImportable", telemetryRecord);
        assertThat(result).isFalse();
    }

    @Test
    void toMarkedAt_shouldUseRecordTimestamp() {
        TelemetryRecord telemetryRecord = createTelemetryRecord(PingStatus.ACCEPTED);
        long timestamp = System.currentTimeMillis();
        telemetryRecord.setTimestamp(timestamp);
        telemetryRecord.setServerReceivedTimestamp(null);

        java.sql.Timestamp result = (java.sql.Timestamp) ReflectionTestUtils.invokeMethod(importJob, "toMarkedAt", telemetryRecord);
        assertThat(result.getTime()).isEqualTo(timestamp);
    }

    @Test
    void toMarkedAt_shouldFallbackToServerReceivedTimestamp() {
        TelemetryRecord telemetryRecord = createTelemetryRecord(PingStatus.ACCEPTED);
        telemetryRecord.setTimestamp(null);
        java.time.Instant serverTime = java.time.Instant.now();
        telemetryRecord.setServerReceivedTimestamp(serverTime);

        java.sql.Timestamp result = (java.sql.Timestamp) ReflectionTestUtils.invokeMethod(importJob, "toMarkedAt", telemetryRecord);
        assertThat(result.toInstant()).isEqualTo(serverTime);
    }

    @Test
    void toMarkedAt_shouldFallbackToNow() {
        TelemetryRecord telemetryRecord = createTelemetryRecord(PingStatus.ACCEPTED);
        telemetryRecord.setTimestamp(null);
        telemetryRecord.setServerReceivedTimestamp(null);

        java.sql.Timestamp result = (java.sql.Timestamp) ReflectionTestUtils.invokeMethod(importJob, "toMarkedAt", telemetryRecord);
        assertThat(result).isNotNull();
    }

    @Test
    void readLastTelemetryId_shouldReturnNullWhenNoState() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("telemetry_raw_ping_import")))
                .thenReturn(List.of());

        String result = (String) ReflectionTestUtils.invokeMethod(importJob, "readLastTelemetryId");
        assertThat(result).isNull();
    }

    @Test
    void readLastTelemetryId_shouldReturnIdWhenStateExists() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("telemetry_raw_ping_import")))
                .thenReturn(List.of("some-telemetry-id"));

        String result = (String) ReflectionTestUtils.invokeMethod(importJob, "readLastTelemetryId");
        assertThat(result).isEqualTo("some-telemetry-id");
    }

    @Test
    void updateLastTelemetryId_shouldInsertState() {
        ReflectionTestUtils.invokeMethod(importJob, "updateLastTelemetryId", "new-telemetry-id");

        verify(jdbcTemplate).update(
                contains("INSERT INTO telemetry_import_state"),
                eq("telemetry_raw_ping_import"),
                eq("new-telemetry-id")
        );
    }

    @Test
    void insertRawPing_shouldReturnFalseOnException() {
        TelemetryRecord telemetryRecord = createTelemetryRecord(PingStatus.ACCEPTED);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(importJob, "insertRawPing", telemetryRecord);
        assertThat(result).isFalse();
    }

    @Test
    void shouldNotReadMongoWhenSchedulingIsDisabled() {
        ReflectionTestUtils.setField(importJob, "schedulingEnabled", false);

        importJob.importNewTelemetryRecords();

        verify(mongoTemplate, never()).find(any(), eq(TelemetryRecord.class));
    }

    @Test
    void shouldFallbackToLocalMongoWhenPrimaryFails() {
        TelemetryRecord accepted = createTelemetryRecord(PingStatus.ACCEPTED);

        java.util.concurrent.atomic.AtomicReference<MongoTemplate> fallbackRef =
                (java.util.concurrent.atomic.AtomicReference<MongoTemplate>)
                        ReflectionTestUtils.getField(importJob, "fallbackMongoTemplate");
        assert fallbackRef != null;
        fallbackRef.set(fallbackMongoTemplate);

        java.util.concurrent.atomic.AtomicReference<MongoTemplate> activeRef =
                (java.util.concurrent.atomic.AtomicReference<MongoTemplate>)
                        ReflectionTestUtils.getField(importJob, "activeMongoTemplate");
        assert activeRef != null;
        activeRef.set(mongoTemplate);

        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("telemetry_raw_ping_import")))
                .thenReturn(List.of());
        when(mongoTemplate.find(any(), eq(TelemetryRecord.class)))
                .thenThrow(new com.mongodb.MongoException("connection refused"));
        when(fallbackMongoTemplate.find(any(), eq(TelemetryRecord.class)))
                .thenReturn(List.of(accepted));

        importJob.importNewTelemetryRecords();

        verify(mongoTemplate).find(any(), eq(TelemetryRecord.class));
        verify(fallbackMongoTemplate).find(any(), eq(TelemetryRecord.class));
        verify(jdbcTemplate).update(
                contains("INSERT INTO raw_user_pings"),
                eq(accepted.getId()),
                any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void shouldReturnEmptyWhenBothPrimaryAndFallbackFail() {
        java.util.concurrent.atomic.AtomicReference<MongoTemplate> fallbackRef =
                (java.util.concurrent.atomic.AtomicReference<MongoTemplate>)
                        ReflectionTestUtils.getField(importJob, "fallbackMongoTemplate");
        assert fallbackRef != null;
        fallbackRef.set(fallbackMongoTemplate);

        java.util.concurrent.atomic.AtomicReference<MongoTemplate> activeRef =
                (java.util.concurrent.atomic.AtomicReference<MongoTemplate>)
                        ReflectionTestUtils.getField(importJob, "activeMongoTemplate");
        assert activeRef != null;
        activeRef.set(mongoTemplate);

        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("telemetry_raw_ping_import")))
                .thenReturn(List.of());
        when(mongoTemplate.find(any(), eq(TelemetryRecord.class)))
                .thenThrow(new com.mongodb.MongoException("connection refused"));
        when(fallbackMongoTemplate.find(any(), eq(TelemetryRecord.class)))
                .thenThrow(new com.mongodb.MongoException("connection refused"));

        importJob.importNewTelemetryRecords();

        verify(fallbackMongoTemplate).find(any(), eq(TelemetryRecord.class));
        verify(jdbcTemplate, never()).update(
                contains("INSERT INTO raw_user_pings"),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    private TelemetryRecord createTelemetryRecord(PingStatus status) {
        TelemetryRecord telemetryRecord = new TelemetryRecord();
        telemetryRecord.setId(new ObjectId().toHexString());
        telemetryRecord.setDeviceId("device-1");
        telemetryRecord.setStoreId(UUID.randomUUID().toString());
        telemetryRecord.setItemId(UUID.randomUUID().toString());
        telemetryRecord.setLat(44.4268);
        telemetryRecord.setLng(26.1025);
        telemetryRecord.setAccuracyMeters(4.5);
        telemetryRecord.setTimestamp(System.currentTimeMillis());
        telemetryRecord.setStatus(status);
        return telemetryRecord;
    }
}
