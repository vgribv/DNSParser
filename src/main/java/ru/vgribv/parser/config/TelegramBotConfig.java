package ru.vgribv.parser.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;


@Configuration
@Log4j2
public class TelegramBotConfig {
    @Bean
    @Primary
    public TelegramClient telegramClient(@Value("${BOT_TOKEN}") String botToken) {
        return new OkHttpTelegramClient(botToken);
    }
}
