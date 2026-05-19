package com.p2ps.proximity.repository;

import com.p2ps.proximity.model.ActiveListLocation;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for active list locations.
 * Spring Data MongoDB automatically translates findByCoordinatesNear
 * into a $near geospatial query using the 2dsphere index.
 */
@Repository
public interface ActiveListLocationRepository extends MongoRepository<ActiveListLocation, String> {

    /**
     * Finds all active list locations within a given distance from a point.
     * Requires a 2dsphere index on the coordinates field.
     *
     * @param point    the user's current location (longitude, latitude)
     * @param distance max distance in meters
     */
    List<ActiveListLocation> findByCoordinatesNear(Point point, Distance distance);

    List<ActiveListLocation> findByListId(String listId);

    void deleteByListId(String listId);

    void deleteByItemId(String itemId);
}