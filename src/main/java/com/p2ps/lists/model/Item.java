package com.p2ps.lists.model;

import com.p2ps.catalog.model.ProductCatalog;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Entity
@Table(name = "items")
@Getter
@Setter
public class Item {

    private static final AtomicLong POSITION_SEQUENCE = new AtomicLong(System.currentTimeMillis());

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = System.currentTimeMillis();
        }
        if (lastUpdatedTimestamp == null) {
            lastUpdatedTimestamp = System.currentTimeMillis();
        }
        if (positionIndex == null) {
            positionIndex = (double) POSITION_SEQUENCE.getAndIncrement();
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_checked", nullable = false)
    private boolean isChecked = false;


    @Column(length = 100)
    private String brand;

    @Column(length = 50)
    private String quantity;

    @PositiveOrZero(message = "Price must be zero or positive")
    private BigDecimal price;

    @Column(length = 50)
    private String category;

    @Column(name = "is_recurrent")
    private boolean isRecurrent = false;

    @Column(name = "position_index")
    private Double positionIndex;

    @Column(name = "last_updated_timestamp")
    private Long lastUpdatedTimestamp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Long createdAt;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id", nullable = false)
    private ShoppingList shoppingList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id")
    private ProductCatalog catalogItem;

    @Column(name = "external_item_id", length = 255)
    private String externalItemId;
}
