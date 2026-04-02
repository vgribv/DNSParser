package ru.vgribv.parser.config;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;

import java.net.InetSocketAddress;
import java.net.Proxy;

@Configuration
public class TelegramBotConfig {
    @Bean
    @Primary
    public TelegramClient telegramClientWithProxy(@Value("${bot.token}") String botToken,
                                         @Value("${proxy.hostname}") String hostname,
                                         @Value("${proxy.port}") int port) {
        Proxy httpProxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(hostname, port));
        OkHttpClient client = new OkHttpClient.Builder().proxy(httpProxy).build();

        return new OkHttpTelegramClient(client, botToken);
    }

    @Bean
    public TelegramClient telegramClientWithoutProxy(@Value("${bot.token}") String botToken) {
        return new OkHttpTelegramClient(botToken);
    }

    @Bean
    public TelegramBotsLongPollingApplication telegramBotsApplication() {
        return new TelegramBotsLongPollingApplication();
    }
}
