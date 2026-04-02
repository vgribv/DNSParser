package ru.vgribv.parser.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "price_history")
@Getter
@Setter
@ToString(exclude = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceHistory {

    public PriceHistory (Product product, Integer price, LocalDateTime createdAt){
        this.price = price;
        this.createdAt = createdAt;
        this.product = product;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "price_history_seq_gen")
    @SequenceGenerator(
            name = "price_history_seq_gen",
            sequenceName = "price_history_sequence",
            allocationSize = 50
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    private Integer price;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

}
