package ru.vgribv.parser.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;

@Entity
@Table(name = "search_filter")
@Getter
@Setter
@ToString
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

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SearchFilter filter)) return false;
        return Objects.equals(id, filter.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
