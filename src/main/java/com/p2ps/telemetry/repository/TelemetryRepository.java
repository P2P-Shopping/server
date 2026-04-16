package com.p2ps.telemetry.repository;

import com.p2ps.telemetry.model.TelemetryRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelemetryRepository extends MongoRepository<TelemetryRecord, String> {

    List<TelemetryRecord> findByStoreIdAndItemId(String storeId, String itemId);
}