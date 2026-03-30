package ru.vgribv.parser.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "product")
@Getter
@Setter
@ToString
public class Product {

    public Product(){}

    public Product (String linkId, String name, Integer discountPrice, Integer fullPrice, Category category, LocalDateTime updatedAt){
        this.name = name;
        this.discountPrice = discountPrice;
        this.fullPrice = fullPrice;
        this.updatedAt = updatedAt;
        this.category = category;
        this.linkId = linkId;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq_gen")
    @SequenceGenerator(
            name = "product_seq_gen",
            sequenceName = "product_sequence",
            allocationSize = 50
    )
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
    private boolean productPurchased = false;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Product product)) return false;
        return Objects.equals(linkId, product.linkId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(linkId);
    }
}
