package ru.vgribv.parser.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;

@Entity
@Table(name = "tracker")
@Getter
@Setter
@ToString
public class Tracker {

    public Tracker(){}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    private String name;

    private String link;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Tracker tracker)) return false;
        return Objects.equals(chatId, tracker.chatId) && Objects.equals(link, tracker.link);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatId, link);
    }
}
