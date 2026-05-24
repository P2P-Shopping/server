package com.p2ps.store.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Polygon;

import java.util.UUID;

@Entity
@Table(name = "store_geofences")
@Getter
@Setter
public class StoreGeofence {

    @Id
    @Column(name = "store_id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "boundary_polygon", nullable = false, columnDefinition = "geometry(Polygon, 4326)")
    private Polygon boundaryPolygon;
}
