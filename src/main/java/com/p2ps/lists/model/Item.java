package com.p2ps.lists.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "items")
@Getter
@Setter
public class Item {

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
}
