package com.p2ps.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "store_inventory_map")
public class StoreInventoryMap {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "map_id")
    private UUID mapId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "estimated_loc_point", columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point estimatedLocPoint;

    @Column(name = "confidence_score", nullable = false)
    private Double confidenceScore;

    @Column(name = "ping_count", nullable = false)
    private Integer pingCount;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
}
