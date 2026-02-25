package com.smartknowledgehub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartknowledgehub.config.OpenSearchProperties;
import com.smartknowledgehub.model.MetadataKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.search", name = "enabled", havingValue = "true")
public class OpenSearchIndexInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(OpenSearchIndexInitializer.class);

    private final WebClient openSearchWebClient;
    private final OpenSearchProperties properties;
    private final ObjectMapper objectMapper;

    public OpenSearchIndexInitializer(@Qualifier("openSearchWebClient") WebClient openSearchWebClient,
                                      OpenSearchProperties properties,
                                      ObjectMapper objectMapper) {
        this.openSearchWebClient = openSearchWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isAutoCreate()) {
            log.info("OpenSearch auto-create disabled. Skipping index initialization.");
            return;
        }
        String index = properties.getIndexName();
        try {
            boolean exists = Boolean.TRUE.equals(openSearchWebClient.head()
                    .uri("/{index}", index)
                    .exchangeToMono(response -> response.statusCode().is2xxSuccessful()
                            ? response.bodyToMono(Void.class).thenReturn(true)
                            : response.bodyToMono(Void.class).thenReturn(false))
                    .block());
            if (exists) {
                log.info("OpenSearch index '{}' already exists.", index);
                return;
            }
            String body = objectMapper.writeValueAsString(defaultMapping());
            openSearchWebClient.put()
                    .uri("/{index}", index)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("OpenSearch index '{}' created.", index);
        } catch (Exception ex) {
            log.warn("Failed to initialize OpenSearch index '{}'", properties.getIndexName(), ex);
        }
    }

    private Map<String, Object> defaultMapping() {
        String analyzer = properties.getAnalyzer();
        String searchAnalyzer = properties.getSearchAnalyzer();

        Map<String, Object> propertiesMap = new LinkedHashMap<>();
        Map<String, Object> textMapping = new LinkedHashMap<>();
        textMapping.put("type", "text");
        if (analyzer != null && !analyzer.isBlank()) {
            textMapping.put("analyzer", analyzer);
        }
        if (searchAnalyzer != null && !searchAnalyzer.isBlank()) {
            textMapping.put("search_analyzer", searchAnalyzer);
        }
        propertiesMap.put("text", textMapping);
        propertiesMap.put(MetadataKeys.FILE_NAME, Map.of("type", "keyword"));
        propertiesMap.put(MetadataKeys.PAGE_NUMBER, Map.of("type", "integer"));
        propertiesMap.put(MetadataKeys.CLASS_NAME, Map.of("type", "keyword"));
        propertiesMap.put(MetadataKeys.METHOD_NAME, Map.of("type", "keyword"));
        propertiesMap.put(MetadataKeys.CHUNK_ID, Map.of("type", "keyword"));
        propertiesMap.put(MetadataKeys.DOCUMENT_ID, Map.of("type", "keyword"));
        propertiesMap.put(MetadataKeys.CHUNK_INDEX, Map.of("type", "integer"));

        Map<String, Object> mappings = new LinkedHashMap<>();
        mappings.put("properties", propertiesMap);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mappings", mappings);
        return body;
    }
}
