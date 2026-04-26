package com.p2ps.lists.model;

import com.p2ps.catalog.model.ProductCatalog;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "items", schema = "public")
@Getter
@Setter
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
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


    @Column(name = "last_updated_timestamp")
    private Long lastUpdatedTimestamp;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id", nullable = false)
    private ShoppingList shoppingList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id")
    private ProductCatalog catalogItem;
}
