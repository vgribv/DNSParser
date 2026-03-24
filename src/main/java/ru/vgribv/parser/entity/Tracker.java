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

    public Tracker(long chatId, String link) {
        this.chatId = chatId;
        this.link = link;
    }

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
        return Objects.equals(id, tracker.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
