package com.smartknowledgehub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartknowledgehub.config.OpenSearchProperties;
import com.smartknowledgehub.model.ChunkSource;
import com.smartknowledgehub.model.MetadataKeys;
import com.smartknowledgehub.model.RetrievedChunk;
import com.smartknowledgehub.model.RetrievalScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "app.search", name = "enabled", havingValue = "true")
public class OpenSearchKeywordSearchService implements KeywordSearchService {
    private static final Logger log = LoggerFactory.getLogger(OpenSearchKeywordSearchService.class);

    private final WebClient openSearchWebClient;
    private final OpenSearchProperties properties;
    private final ObjectMapper objectMapper;

    public OpenSearchKeywordSearchService(@Qualifier("openSearchWebClient") WebClient openSearchWebClient,
                                          OpenSearchProperties properties,
                                          ObjectMapper objectMapper) {
        this.openSearchWebClient = openSearchWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<RetrievedChunk> search(String query, int topK, RetrievalScope scope) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", topK);
        body.put("query", buildQuery(query, scope));

        return openSearchWebClient.post()
                .uri("/{index}/_search", properties.getIndexName())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .filter(response -> response != null && !response.isBlank())
                .flatMap(response -> Mono.fromCallable(() -> parse(response)))
                .onErrorResume(ex -> {
                    log.warn("OpenSearch query failed", ex);
                    return Mono.just(List.of());
                })
                .blockOptional()
                .orElse(List.of());
    }

    private Map<String, Object> buildQuery(String query, RetrievalScope scope) {
        if (scope == null || scope.isEmpty()) {
            return Map.of("match", Map.of("text", query));
        }
        List<Map<String, Object>> filters = buildFilters(scope);
        return Map.of(
                "bool",
                Map.of(
                        "must", List.of(Map.of("match", Map.of("text", query))),
                        "filter", filters
                )
        );
    }

    private List<Map<String, Object>> buildFilters(RetrievalScope scope) {
        List<Map<String, Object>> filters = new ArrayList<>();
        addTermFilter(filters, MetadataKeys.DOCUMENT_ID, scope.getDocumentId());
        addTermFilter(filters, MetadataKeys.FILE_NAME, scope.getFileName());
        addTermFilter(filters, MetadataKeys.CLASS_NAME, scope.getClassName());
        addTermFilter(filters, MetadataKeys.METHOD_NAME, scope.getMethodName());
        return filters;
    }

    private void addTermFilter(List<Map<String, Object>> filters, String field, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        filters.add(Map.of("term", Map.of(field, value)));
    }

    private List<RetrievedChunk> parse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode hits = root.path("hits").path("hits");
        List<RetrievedChunk> results = new ArrayList<>();
        if (!hits.isArray()) {
            return results;
        }
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            String text = source.path("text").asText("");
            String chunkId = source.path(MetadataKeys.CHUNK_ID).asText(null);
            String id = chunkId != null ? chunkId : hit.path("_id").asText(null);
            double score = hit.path("_score").asDouble(0.0);
            ChunkSource chunkSource = new ChunkSource(
                    asNullableText(source.path(MetadataKeys.FILE_NAME)),
                    asNullableInteger(source.path(MetadataKeys.PAGE_NUMBER)),
                    asNullableText(source.path(MetadataKeys.CLASS_NAME)),
                    asNullableText(source.path(MetadataKeys.METHOD_NAME))
            );
            results.add(new RetrievedChunk(id, text, score, chunkSource));
        }
        return results;
    }

    private String asNullableText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private Integer asNullableInteger(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asInt();
    }
}
