package com.smartknowledgehub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartknowledgehub.config.OpenSearchProperties;
import com.smartknowledgehub.model.ChunkSource;
import com.smartknowledgehub.model.MetadataKeys;
import com.smartknowledgehub.model.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
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
    public List<RetrievedChunk> search(String query, int topK) {
        // 使用 match 查询，走 BM25 打分
        Map<String, Object> body = Map.of(
                "size", topK,
                "query", Map.of("match", Map.of("text", query))
        );

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

    private List<RetrievedChunk> parse(String response) throws Exception {
        // 解析 OpenSearch hits
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
                    source.path(MetadataKeys.FILE_NAME).asText(null),
                    source.path(MetadataKeys.PAGE_NUMBER).isMissingNode() ? null : source.path(MetadataKeys.PAGE_NUMBER).asInt(),
                    source.path(MetadataKeys.CLASS_NAME).asText(null),
                    source.path(MetadataKeys.METHOD_NAME).asText(null)
            );
            results.add(new RetrievedChunk(id, text, score, chunkSource));
        }
        return results;
    }

}

