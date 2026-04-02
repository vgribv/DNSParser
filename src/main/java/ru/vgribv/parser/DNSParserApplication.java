package ru.vgribv.parser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(excludeName = {
		"org.telegram.telegrambots.springboot.longpolling.starter.TelegramBotStarterConfiguration"
})
@EnableScheduling
@EnableAsync
@EnableRetry
public class DNSParserApplication {
	public static void main(String[] args) {
		SpringApplication.run(DNSParserApplication.class);
	}
}
