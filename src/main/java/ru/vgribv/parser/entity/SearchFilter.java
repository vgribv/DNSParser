package ru.vgribv.parser.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "search_filter")
@Data
public class SearchFilter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    private String keyword;

    private String category;

    @Column(name = "max_price")
    private Integer maxPrice;

    public SearchFilter() {}

    public SearchFilter(long chatId) {
        this.chatId = chatId;
    }
}
