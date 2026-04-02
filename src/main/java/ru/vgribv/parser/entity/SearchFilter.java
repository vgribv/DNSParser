package ru.vgribv.parser.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "search_filter")
@Getter
@Setter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchFilter {

    public SearchFilter(long chatId) {
        this.chatId = chatId;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    private String keyword;

    private String category;

    @Column(name = "max_price")
    private Integer maxPrice;

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
