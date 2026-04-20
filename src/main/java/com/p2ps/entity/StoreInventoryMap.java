package com.p2ps.entity;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "store_inventory_map",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_store_item",
                        columnNames = {"store_id", "item_id"}
                )
        }
)
public class StoreInventoryMap {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "map_id", updatable = false, nullable = false)
    private UUID mapId; // [FIX] Redenumit din 'id' în 'mapId'

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

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated; // [FIX] Am adăugat câmpul lipsă pentru Data Decay

    public StoreInventoryMap() {}

    public StoreInventoryMap(UUID storeId, UUID itemId, Point estimatedLocPoint, Double confidenceScore, Integer pingCount, LocalDateTime lastUpdated) {
        this.storeId = storeId;
        this.itemId = itemId;
        this.estimatedLocPoint = estimatedLocPoint;
        this.confidenceScore = confidenceScore;
        this.pingCount = pingCount;
        this.lastUpdated = lastUpdated;
    }

    // --- Getters & Setters ---

    public UUID getMapId() { return mapId; }
    public void setMapId(UUID mapId) { this.mapId = mapId; }

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

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}