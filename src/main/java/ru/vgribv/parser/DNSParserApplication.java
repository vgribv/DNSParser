package ru.vgribv.parser;

import lombok.extern.log4j.Log4j2;
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
@Log4j2
public class DNSParserApplication {
	public static void main(String[] args) {
		String proxyHost = System.getenv("PROXY_HOST") != null ? System.getenv("PROXY_HOST") : "127.0.0.1";
		String proxyPort = System.getenv("PROXY_PORT") != null ? System.getenv("PROXY_PORT") : "1111";

		System.setProperty("http.proxyHost", proxyHost);
		System.setProperty("http.proxyPort", proxyPort);
		System.setProperty("https.proxyHost", proxyHost);
		System.setProperty("https.proxyPort", proxyPort);
		log.info("Proxy Set: {}:{}", proxyHost, proxyPort);

		SpringApplication.run(DNSParserApplication.class, args);
	}
}
