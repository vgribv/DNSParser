package ru.vgribv.parser.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.management.ManagementFactory;
import javax.management.ObjectName;

@Configuration
public class MetricsConfig {

    @Bean
    public void registerVirtualThreadMetrics(MeterRegistry registry) {
        Gauge.builder("jvm.threads.virtual.count", this::getJmxValue)
                .description("Current number of virtual threads")
                .register(registry);
    }

    private double getJmxValue() {
        try {
            var mbs = ManagementFactory.getPlatformMBeanServer();
            var name = new ObjectName("java.lang:type=Threading");
            return ((Long) mbs.getAttribute(name, "VirtualThreadCount")).doubleValue();
        } catch (Exception e) {
            return 0.0;
        }
    }
}
