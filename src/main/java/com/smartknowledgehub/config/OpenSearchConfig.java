package com.smartknowledgehub.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ConditionalOnProperty(prefix = "app.search", name = "enabled", havingValue = "true")
public class OpenSearchConfig {

    @Bean
    public WebClient openSearchWebClient(OpenSearchProperties properties) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(properties.getBaseUrl());
        if (StringUtils.hasText(properties.getUsername())) {
            builder.filter(ExchangeFilterFunctions.basicAuthentication(
                    properties.getUsername(),
                    properties.getPassword() == null ? "" : properties.getPassword()
            ));
        }
        return builder.build();
    }
}
