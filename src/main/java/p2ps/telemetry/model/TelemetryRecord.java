package p2ps.telemetry.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "telemetry_records")
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
    private Long serverReceivedTimestamp;
}