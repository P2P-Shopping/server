package com.p2ps.entity;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;
import java.util.UUID;

@Entity
@Table(name = "store_inventory_map")
public class StoreInventoryMap {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "estimated_loc_point", columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point estimatedLocPoint;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "ping_count")
    private Integer pingCount;

    public StoreInventoryMap() {}

    public StoreInventoryMap(UUID storeId, UUID itemId, Point estimatedLocPoint, Double confidenceScore, Integer pingCount) {
        this.storeId = storeId;
        this.itemId = itemId;
        this.estimatedLocPoint = estimatedLocPoint;
        this.confidenceScore = confidenceScore;
        this.pingCount = pingCount;
    }

    // Getters & Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }
    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }
    public Point getEstimatedLocPoint() { return estimatedLocPoint; }
    public void setEstimatedLocPoint(Point estimatedLocPoint) { this.estimatedLocPoint = estimatedLocPoint; }
    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }
    public Integer getPingCount() { return pingCount; }
    public void setPingCount(Integer pingCount) { this.pingCount = pingCount; }
}