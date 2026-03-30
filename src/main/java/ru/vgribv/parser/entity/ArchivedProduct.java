package ru.vgribv.parser.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "archived_product")
@Getter
@Setter
@ToString
public class ArchivedProduct {

    public ArchivedProduct(){}

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "archived_product_seq_gen")
    @SequenceGenerator(
            name = "archived_product_seq_gen",
            sequenceName = "archived_product_sequence",
            allocationSize = 50
    )
    private Long id;

    @Column(name = "link_id", nullable = false)
    private String linkId;

    private String name;

    @Column(name = "discount_price")
    private Integer discountPrice;

    @Column(name = "full_price")
    private Integer fullPrice;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ArchivedProduct that)) return false;
        return Objects.equals(linkId, that.linkId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(linkId);
    }
}
