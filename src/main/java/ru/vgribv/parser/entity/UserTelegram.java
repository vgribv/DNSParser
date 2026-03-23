package ru.vgribv.parser.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "user_telegram")
@Data
public class UserTelegram {

    public UserTelegram(){}

    public UserTelegram (long chatId){
        this.chatId = chatId;
    }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", unique = true, nullable = false)
    private Long chatId;

    private String name;

    @Column(name = "is_active")
    private boolean isActive;
}
