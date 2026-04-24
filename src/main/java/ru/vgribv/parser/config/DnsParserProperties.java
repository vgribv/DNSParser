package ru.vgribv.parser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "dns")
public class DnsParserProperties {

    private String browserPath;
    private String realProfilePath;
    private String city;

    private final Link link = new Link();

    private final Proxy proxy = new Proxy();

    private final Retry retry = new Retry();

    @Data
    public static class Link {

        private String prefix;
        private String productsFilters;
        private String referer;
        private String ajaxState;

    }

    @Data
    public static class Proxy {
        private String host;
        private int port;

    }

    @Data
    public static class Retry {
        private int maxAttempts;
        private int backoffDelay;
    }
}
