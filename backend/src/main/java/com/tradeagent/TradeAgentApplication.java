package com.tradeagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Spring Boot entrypoint for the Java TradeAgent backend. */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class TradeAgentApplication {
    /** Start the backend process. */
    public static void main(String[] args) {
        SpringApplication.run(TradeAgentApplication.class, args);
    }
}

