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
		SpringApplication.run(DNSParserApplication.class, args);
	}
}
