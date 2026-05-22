package com.p2ps.telemetry.services;

import com.p2ps.telemetry.model.PingStatus;
import com.p2ps.telemetry.model.TelemetryRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "telemetry.raw-ping-import.enabled", havingValue = "true", matchIfMissing = true)
@DependsOn("locationProcessorWorker")
@RequiredArgsConstructor
@Slf4j
public class TelemetryRawPingImportJob {

    private static final String JOB_NAME = "telemetry_raw_ping_import";
    private static final String DEFAULT_LOC_PROVIDER = "GPS";
    private static final String SYSTEM_USER_EMAIL = "telemetry-import@p2ps.local";
    private static final String SYSTEM_LIST_ID = "00000000-0000-0000-0000-000000000001";

    private final MongoTemplate mongoTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Value("${app.scheduling.enabled:true}")
    private boolean schedulingEnabled;

    @Value("${telemetry.raw-ping-import.enabled:true}")
    private boolean importEnabled;

    @Value("${telemetry.raw-ping-import.batch-size:500}")
    private int batchSize;

    @Value("${telemetry.raw-ping-import.auto-provision-dimensions:true}")
    private boolean autoProvisionDimensions;

    private volatile Boolean postgresDetected;

    @PostConstruct
    public void initialize() {
        log.info("[TELEMETRY_IMPORT] Initializing Mongo telemetry to raw_user_pings import job.");

        if (!isPostgreSQL()) {
            log.info("[TELEMETRY_IMPORT] Import job disabled because datasource is not PostgreSQL.");
            return;
        }

        if (!requiredTelemetrySchemaPresent()) {
            log.info("[TELEMETRY_IMPORT] Required telemetry schema is not present yet; skipping import job initialization.");
            return;
        }

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS telemetry_import_state (
                job_name VARCHAR(100) PRIMARY KEY,
                last_telemetry_id VARCHAR(64),
                updated_at TIMESTAMP DEFAULT NOW()
            )
        """);
        jdbcTemplate.execute("""
            ALTER TABLE raw_user_pings
            ADD COLUMN IF NOT EXISTS source_telemetry_id VARCHAR(64),
            ADD COLUMN IF NOT EXISTS external_store_id VARCHAR(255),
            ADD COLUMN IF NOT EXISTS external_item_id VARCHAR(255)
        """);
        jdbcTemplate.execute("""
            ALTER TABLE store_geofences
            ADD COLUMN IF NOT EXISTS external_store_id VARCHAR(255)
        """);
        jdbcTemplate.execute("""
            ALTER TABLE items
            ADD COLUMN IF NOT EXISTS external_item_id VARCHAR(255)
        """);
        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS idx_raw_user_pings_source_telemetry_id
            ON raw_user_pings (source_telemetry_id)
        """);
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_raw_user_pings_external_ids
            ON raw_user_pings (external_store_id, external_item_id)
        """);
        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS idx_store_geofences_external_store_id
            ON store_geofences (external_store_id)
            WHERE external_store_id IS NOT NULL
        """);
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_items_external_item_id
            ON items (external_item_id)
            WHERE external_item_id IS NOT NULL
        """);
        jdbcTemplate.execute("""
            ALTER TABLE store_geofences
            ADD COLUMN IF NOT EXISTS entry_point GEOMETRY(Point, 4326),
            ADD COLUMN IF NOT EXISTS exit_point GEOMETRY(Point, 4326)
        """);
        if (autoProvisionDimensions) {
            ensureSystemImportList();
        }
        log.info("[TELEMETRY_IMPORT] Import job initialized successfully.");
    }

    private boolean requiredTelemetrySchemaPresent() {
        return tableExists("store_geofences")
                && tableExists("items")
                && tableExists("users")
                && tableExists("shopping_lists");
    }

    private boolean tableExists(String tableName) {
        try {
            Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name = ?
                )
            """, Boolean.class, tableName);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.debug("[TELEMETRY_IMPORT] Could not inspect table {}.", tableName, e);
            return false;
        }
    }

    @Scheduled(fixedDelayString = "${telemetry.raw-ping-import.fixed-delay-ms:30000}")
    public void importNewTelemetryRecords() {
        if (!schedulingEnabled || !importEnabled || !isPostgreSQL()) {
            return;
        }

        String lastTelemetryId = readLastTelemetryId();
        List<TelemetryRecord> records = fetchNextRecords(lastTelemetryId);
        if (records.isEmpty()) {
            return;
        }

        int inserted = 0;
        int skipped = 0;
        for (TelemetryRecord telemetryRecord : records) {
            if (!isImportable(telemetryRecord)) {
                skipped++;
                continue;
            }

            if (autoProvisionDimensions) {
                ensureDimensions(telemetryRecord);
            }

            if (insertRawPing(telemetryRecord)) {
                inserted++;
            } else {
                skipped++;
            }
        }

        updateLastTelemetryId(records.getLast().getId());
        log.info("[TELEMETRY_IMPORT] Processed {} telemetry records: inserted={}, skipped={}",
                records.size(), inserted, skipped);
    }

    private List<TelemetryRecord> fetchNextRecords(String lastTelemetryId) {
        Query query = new Query();
        if (lastTelemetryId != null && ObjectId.isValid(lastTelemetryId)) {
            query.addCriteria(Criteria.where("_id").gt(new ObjectId(lastTelemetryId)));
        }

        query.with(Sort.by(Sort.Direction.ASC, "_id"))
                .limit(Math.max(1, batchSize));

        return mongoTemplate.find(query, TelemetryRecord.class);
    }

    private void ensureSystemImportList() {
        jdbcTemplate.update("""
            INSERT INTO users (first_name, last_name, email, password, token_version, created_at)
            VALUES ('Telemetry', 'Import', ?, 'system-user-no-login', 0, NOW())
            ON CONFLICT (email) DO NOTHING
        """, SYSTEM_USER_EMAIL);

        jdbcTemplate.update("""
            INSERT INTO shopping_lists (id, title, user_id, category)
            SELECT ?::uuid, 'Imported telemetry items', id, 'NORMAL'
            FROM users
            WHERE email = ?
            ON CONFLICT (id) DO NOTHING
        """, SYSTEM_LIST_ID, SYSTEM_USER_EMAIL);
    }

    private void ensureDimensions(TelemetryRecord telemetryRecord) {
        ensureStore(telemetryRecord);
        ensureItem(telemetryRecord);
    }

    private void ensureStore(TelemetryRecord telemetryRecord) {
        String internalStoreId = toInternalUuid("store", telemetryRecord.getStoreId());

        jdbcTemplate.update("""
            INSERT INTO store_geofences (
                store_id,
                external_store_id,
                name,
                boundary_polygon,
                floor_level,
                entry_point,
                exit_point
            )
            VALUES (
                ?::uuid,
                ?,
                ?,
                ST_Buffer(ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, 100)::geometry,
                0,
                ST_SetSRID(ST_MakePoint(?, ?), 4326),
                ST_SetSRID(ST_MakePoint(?, ?), 4326)
            )
            ON CONFLICT (store_id) DO NOTHING
        """,
                internalStoreId,
                telemetryRecord.getStoreId(),
                telemetryRecord.getStoreName() != null && !telemetryRecord.getStoreName().isBlank()
                        ? telemetryRecord.getStoreName()
                        : "Imported Store " + telemetryRecord.getStoreId(),
                telemetryRecord.getLng(),
                telemetryRecord.getLat(),
                telemetryRecord.getLng(),
                telemetryRecord.getLat(),
                telemetryRecord.getLng(),
                telemetryRecord.getLat());
    }

    private void ensureItem(TelemetryRecord telemetryRecord) {
        String internalItemId = toInternalUuid("item", telemetryRecord.getItemId());
        long itemTimestamp = telemetryRecord.getTimestamp() != null
                ? telemetryRecord.getTimestamp()
                : System.currentTimeMillis();

        jdbcTemplate.update("""
            INSERT INTO items (
                id,
                external_item_id,
                name,
                is_checked,
                price,
                category,
                is_recurrent,
                last_updated_timestamp,
                created_at,
                version,
                list_id
            )
            VALUES (
                ?::uuid,
                ?,
                ?,
                false,
                0,
                'TELEMETRY_IMPORTED',
                false,
                ?,
                ?,
                0,
                ?::uuid
            )
            ON CONFLICT (id) DO NOTHING
        """,
                internalItemId,
                telemetryRecord.getItemId(),
                telemetryRecord.getItemName() != null && !telemetryRecord.getItemName().isBlank()
                        ? telemetryRecord.getItemName()
                        : "Imported Item " + telemetryRecord.getItemId(),
                itemTimestamp,
                itemTimestamp,
                SYSTEM_LIST_ID);
    }

    private boolean isImportable(TelemetryRecord telemetryRecord) {
        return telemetryRecord.getId() != null
                && telemetryRecord.getStatus() == PingStatus.ACCEPTED
                && telemetryRecord.getLat() != null
                && telemetryRecord.getLng() != null
                && hasText(telemetryRecord.getStoreId())
                && hasText(telemetryRecord.getItemId());
    }

    private boolean insertRawPing(TelemetryRecord telemetryRecord) {
        String internalStoreId = toInternalUuid("store", telemetryRecord.getStoreId());
        String internalItemId = toInternalUuid("item", telemetryRecord.getItemId());

        try {
            int rows = jdbcTemplate.update("""
                INSERT INTO raw_user_pings (
                    source_telemetry_id,
                    store_id,
                    item_id,
                    external_store_id,
                    external_item_id,
                    location_point,
                    accuracy_m,
                    loc_provider,
                    marked_at
                )
                VALUES (?, ?::uuid, ?::uuid, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, ?)
                ON CONFLICT (source_telemetry_id) DO NOTHING
            """,
                    telemetryRecord.getId(),
                    internalStoreId,
                    internalItemId,
                    telemetryRecord.getStoreId(),
                    telemetryRecord.getItemId(),
                    telemetryRecord.getLng(),
                    telemetryRecord.getLat(),
                    telemetryRecord.getAccuracyMeters(),
                    DEFAULT_LOC_PROVIDER,
                    toMarkedAt(telemetryRecord));
            return rows > 0;
        } catch (Exception e) {
            log.warn("[TELEMETRY_IMPORT] Skipping telemetry record {} because raw ping insert failed.",
                    telemetryRecord.getId(), e);
            return false;
        }
    }

    private Timestamp toMarkedAt(TelemetryRecord telemetryRecord) {
        if (telemetryRecord.getTimestamp() != null) {
            return Timestamp.from(Instant.ofEpochMilli(telemetryRecord.getTimestamp()));
        }
        if (telemetryRecord.getServerReceivedTimestamp() != null) {
            return Timestamp.from(telemetryRecord.getServerReceivedTimestamp());
        }
        return Timestamp.from(Instant.now());
    }

    private String readLastTelemetryId() {
        List<String> ids = jdbcTemplate.queryForList("""
            SELECT last_telemetry_id
            FROM telemetry_import_state
            WHERE job_name = ?
        """, String.class, JOB_NAME);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private void updateLastTelemetryId(String telemetryId) {
        jdbcTemplate.update("""
            INSERT INTO telemetry_import_state (job_name, last_telemetry_id, updated_at)
            VALUES (?, ?, NOW())
            ON CONFLICT (job_name)
            DO UPDATE SET last_telemetry_id = EXCLUDED.last_telemetry_id,
                          updated_at = EXCLUDED.updated_at
        """, JOB_NAME, telemetryId);
    }

    private String toInternalUuid(String namespace, String value) {
        if (isUuid(value)) {
            return value;
        }
        return UUID.nameUUIDFromBytes((namespace + ":" + value).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    private boolean isPostgreSQL() {
        if (postgresDetected != null) {
            return postgresDetected;
        }
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            postgresDetected = productName != null
                    && productName.toLowerCase(Locale.ROOT).contains("postgres");
            return postgresDetected;
        } catch (SQLException e) {
            log.warn("[TELEMETRY_IMPORT] Could not inspect database type. Import job disabled.", e);
            postgresDetected = false;
            return false;
        }
    }
}
