package ru.vgribv.parser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableRetry
public class DNSParserApplication {
	public static void main(String[] args) {
		String proxyHost = System.getenv("PROXY_HOST") != null ? System.getenv("PROXY_HOST") : "172.17.0.1";
		String proxyPort = System.getenv("PROXY_PORT") != null ? System.getenv("PROXY_PORT") : "1111";

		System.setProperty("http.proxyHost", proxyHost);
		System.setProperty("http.proxyPort", proxyPort);
		System.setProperty("https.proxyHost", proxyHost);
		System.setProperty("https.proxyPort", proxyPort);

		SpringApplication.run(DNSParserApplication.class, args);
	}
}
