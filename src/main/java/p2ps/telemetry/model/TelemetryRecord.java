package p2ps.telemetry.model;

import java.time.Instant;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "telemetry_records")
@CompoundIndex(def = "{'storeId': 1, 'itemId': 1}")
public class TelemetryRecord {

    @Id
    private String id;

    private String deviceId;

    @Indexed
    private String storeId;

    @Indexed
    private String itemId;

    private Double lat;
    private Double lng;
    private Double accuracyMeters;

    private Long timestamp;

    @Indexed(expireAfter = "94608000s") //expires after 3 years
    private Instant serverReceivedTimestamp;
}
