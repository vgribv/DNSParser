package ru.vgribv.parser.config;

import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;

import java.net.InetSocketAddress;
import java.net.Proxy;

@Configuration
@Log4j2
public class TelegramBotConfig {
    @Bean
    @Primary
    public TelegramClient telegramClientWithProxy(@Value("${BOT_TOKEN}") String botToken,
                                         @Value("${PROXY_HOST}") String hostname,
                                         @Value("${PROXY_PORT}") int port) {
        log.info("!!! ИНИЦИАЛИЗАЦИЯ TELEGRAM_CLIENT: Прокси={}:{}, Токен_длина={} !!!",
                hostname, port, (botToken != null ? botToken.length() : "NULL"));

        Proxy httpProxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(hostname, port));
        OkHttpClient client = new OkHttpClient.Builder().proxy(httpProxy).build();
        log.info("!!! OKHTTP_CLIENT СОЗДАН УСПЕШНО !!!");

        return new OkHttpTelegramClient(client, botToken);
    }
}
