package ru.vgribv.parser.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "tracker")
@Data
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
}
