package com.p2ps.telemetry.repository;

import com.p2ps.telemetry.model.TelemetryRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TelemetryRepository extends MongoRepository<TelemetryRecord, String> {
}