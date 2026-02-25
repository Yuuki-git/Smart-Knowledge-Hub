package com.smartknowledgehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SmartKnowledgeHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartKnowledgeHubApplication.class, args);
    }
}
