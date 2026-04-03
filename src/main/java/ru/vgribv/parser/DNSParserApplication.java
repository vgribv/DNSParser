package ru.vgribv.parser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.vgribv.parser.config.TelegramBotConfig;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableRetry
@Import(TelegramBotConfig.class)
public class DNSParserApplication {
	public static void main(String[] args) {
		System.setProperty("http.proxyHost", "127.0.0.1");
		System.setProperty("http.proxyPort", "1111");
		System.setProperty("https.proxyHost", "127.0.0.1");
		System.setProperty("https.proxyPort", "1111");
		SpringApplication.run(DNSParserApplication.class, args);
	}
}
