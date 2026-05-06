package com.p2ps.lists.model;

import com.p2ps.auth.model.Users;
import com.p2ps.catalog.model.ProductCatalog;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "user_product_history")
@Getter
@Setter
public class UserProductHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id")
    private ProductCatalog catalogItem;

    @Column(name = "custom_name", nullable = false)
    private String customName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "last_added_timestamp")
    private Long lastAddedTimestamp;
}