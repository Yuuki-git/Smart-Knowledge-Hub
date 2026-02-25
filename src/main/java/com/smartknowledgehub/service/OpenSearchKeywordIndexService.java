package com.smartknowledgehub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartknowledgehub.config.OpenSearchProperties;
import com.smartknowledgehub.model.ChunkPayload;
import com.smartknowledgehub.model.ChunkSource;
import com.smartknowledgehub.model.MetadataKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "app.search", name = "enabled", havingValue = "true")
public class OpenSearchKeywordIndexService implements KeywordIndexService {
    private static final Logger log = LoggerFactory.getLogger(OpenSearchKeywordIndexService.class);

    private final WebClient openSearchWebClient;
    private final OpenSearchProperties properties;
    private final ObjectMapper objectMapper;

    public OpenSearchKeywordIndexService(@Qualifier("openSearchWebClient") WebClient openSearchWebClient,
                                         OpenSearchProperties properties,
                                         ObjectMapper objectMapper) {
        this.openSearchWebClient = openSearchWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void index(List<ChunkPayload> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        // 批量写入索引，减少请求次数
        Mono.fromCallable(() -> buildNdjson(chunks))
                .flatMap(ndjson -> openSearchWebClient.post()
                        .uri("/_bulk")
                        .contentType(MediaType.APPLICATION_NDJSON)
                        .bodyValue(ndjson)
                        .retrieve()
                        .bodyToMono(String.class))
                .doOnSuccess(resp -> log.info("Indexed {} chunks into OpenSearch index {}", chunks.size(), properties.getIndexName()))
                .onErrorResume(ex -> {
                    log.warn("OpenSearch bulk index failed", ex);
                    return Mono.empty();
                })
                .block();
    }

    private String buildNdjson(List<ChunkPayload> chunks) throws Exception {
        StringBuilder ndjson = new StringBuilder();
        for (ChunkPayload chunk : chunks) {
            String id = chunk.getId() != null ? chunk.getId() : UUID.randomUUID().toString();
            Map<String, Object> source = buildSource(chunk, id);
            Map<String, Object> action = Map.of("index", Map.of("_index", properties.getIndexName(), "_id", id));
            ndjson.append(objectMapper.writeValueAsString(action)).append("\n");
            ndjson.append(objectMapper.writeValueAsString(source)).append("\n");
        }
        return ndjson.toString();
    }

    private Map<String, Object> buildSource(ChunkPayload chunk, String id) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(MetadataKeys.CHUNK_ID, id);
        source.put("text", chunk.getText());
        if (chunk.getAttributes() != null) {
            source.putAll(chunk.getAttributes());
        }
        ChunkSource chunkSource = chunk.getSource();
        if (chunkSource != null) {
            putIfPresent(source, MetadataKeys.FILE_NAME, chunkSource.getFileName());
            putIfPresent(source, MetadataKeys.PAGE_NUMBER, chunkSource.getPageNumber());
            putIfPresent(source, MetadataKeys.CLASS_NAME, chunkSource.getClassName());
            putIfPresent(source, MetadataKeys.METHOD_NAME, chunkSource.getMethodName());
        }
        return source;
    }
    // 仅在字段存在时写入，减少重复判断
    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        target.put(key, value);
    }
}

