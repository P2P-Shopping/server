package com.p2ps.catalog.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.p2ps.store.model.StoreGeofence;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "store_prices")
@Getter
@Setter
public class StorePrice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalog_id", nullable = false)
    private ProductCatalog catalogItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private StoreGeofence store;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "last_updated_at", nullable = false)
    private LocalDateTime lastUpdatedAt;
}
