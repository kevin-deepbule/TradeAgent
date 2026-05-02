package com.tradeagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** HTTP client configuration for internal service calls. */
@Configuration
public class RestClientConfig {
    /** Create the client used to call the local AkShare adapter. */
    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}

