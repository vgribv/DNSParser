package ru.vgribv.parser.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Entity
@Table(name = "category")
@Data
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", unique = true, nullable = false)
    private String categoryId;

    private String name;

    @ToString.Exclude
    @OneToMany(mappedBy = "category")
    private List<Product> productList;
}
