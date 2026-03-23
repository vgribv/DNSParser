package ru.vgribv.parser.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "goods")
@Data
public class Product {

    public Product(){}

    public Product (String linkId, String name, int discountPrice, int fullPrice, Category category, LocalDateTime updatedAt){
        this.name = name;
        this.discountPrice = discountPrice;
        this.fullPrice = fullPrice;
        this.updatedAt = updatedAt;
        this.category = category;
        this.linkId = linkId;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", unique = true, nullable = false)
    private String linkId;

    private String name;

    @Column(name = "discount_price")
    private Integer discountPrice;

    @Column(name = "full_price")
    private Integer fullPrice;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    private Integer oldDiscountPrice;

    @Transient
    private boolean goodPurchased = false;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;
}
