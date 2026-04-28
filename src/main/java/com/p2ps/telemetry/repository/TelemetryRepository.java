package com.p2ps.telemetry.repository;

import com.p2ps.telemetry.model.TelemetryRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelemetryRepository extends MongoRepository<TelemetryRecord, String> {

    List<TelemetryRecord> findByStoreIdAndItemId(String storeId, String itemId);

    Optional<TelemetryRecord> findTopByDeviceIdAndStoreIdAndItemIdOrderByTimestampDesc(String deviceId, String storeId, String itemId);

    @Query(value = "{ 'timestamp': { $lt: ?0 } }", delete = true)
    void deleteByTimestampBefore(long timestamp);
}