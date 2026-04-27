package com.p2ps.catalog.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "p2p_product_catalog")
@Getter
@Setter
public class ProductCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "generic_name", nullable = false)
    private String genericName;

    @Column(name = "specific_name", nullable = false)
    private String specificName;

    @Column(length = 100)
    private String brand;
    
    @Column(length = 50)
    private String category;
    
    @Column(name = "estimated_price", precision = 10, scale = 2)
    private BigDecimal estimatedPrice;

    @Column(name = "purchase_count", nullable = false)
    private Integer purchaseCount = 0;
}