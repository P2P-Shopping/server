package com.p2ps.telemetry.model;

import java.time.Instant;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "telemetry_records")
@CompoundIndexes({
        @CompoundIndex(def = "{'storeId': 1, 'itemId': 1}"),
        @CompoundIndex(name = "unique_device_timestamp", def = "{'deviceId': 1, 'timestamp': 1}", unique = true)
})
public class TelemetryRecord {

    @Id
    private String id;

    private String deviceId;

    private String storeId;

    private String itemId;

    private Double lat;
    private Double lng;
    private Double accuracyMeters;

    private Long timestamp;

    @Indexed(expireAfter = "94608000s")
    private Instant serverReceivedTimestamp;
}