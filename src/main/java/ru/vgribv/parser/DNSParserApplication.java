package ru.vgribv.parser;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.vgribv.parser.service.ParserService;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableRetry
public class DNSParserApplication {

	void main() {
		SpringApplication.run(DNSParserApplication.class);
	}

	@Bean
	public CommandLineRunner test(ParserService parserService){
		return _ ->{
			//parserService.manualRun();
		};
	}

}
