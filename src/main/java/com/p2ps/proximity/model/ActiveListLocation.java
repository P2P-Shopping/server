package com.p2ps.proximity.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Stores the geographic location of an active shopping list item,
 * used for proximity matching when a user walks nearby.
 *
 * The coordinates array is indexed with a 2dsphere index so MongoDB
 * can efficiently execute $near geospatial queries.
 */
@Data
@Document(collection = "active_list_locations")
public class ActiveListLocation {

    @Id
    private String id;

    private String listId;
    private String itemId;
    private String ownerEmail;

    /**
     * GeoJSON Point: [longitude, latitude].
     * MongoDB $near requires longitude first.
     */
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private double[] coordinates;

    private Instant createdAt;
    private Instant updatedAt;
}