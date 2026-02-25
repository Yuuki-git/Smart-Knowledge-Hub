package com.smartknowledgehub.service;

import com.smartknowledgehub.config.RetrievalProperties;
import com.smartknowledgehub.model.ChunkPayload;
import com.smartknowledgehub.model.ChunkSource;
import com.smartknowledgehub.model.MetadataKeys;
import com.smartknowledgehub.model.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VectorSearchService {
    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

    private final VectorStore vectorStore;
    private final RetrievalProperties properties;

    public VectorSearchService(VectorStore vectorStore, RetrievalProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public List<RetrievedChunk> search(String query, int topK) {
        int resolvedTopK = topK > 0 ? topK : properties.getTopK();
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(resolvedTopK)
                .similarityThreshold(properties.getSimilarityThreshold())
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        return results.stream()
                .map(this::map)
                .collect(Collectors.toList());
    }

    public void index(List<ChunkPayload> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        List<Document> documents = new ArrayList<>();
        for (ChunkPayload chunk : chunks) {
            documents.add(toDocument(chunk));
        }
        vectorStore.add(documents);
        log.info("Indexed {} chunks into vector store.", documents.size());
    }

    private Document toDocument(ChunkPayload payload) {
        String id = payload.getId() != null ? payload.getId() : UUID.randomUUID().toString();
        Map<String, Object> metadata = new HashMap<>();
        if (payload.getAttributes() != null) {
            metadata.putAll(payload.getAttributes());
        }
        ChunkSource source = payload.getSource();
        if (source != null) {
            metadata.putIfAbsent(MetadataKeys.FILE_NAME, source.getFileName());
            metadata.putIfAbsent(MetadataKeys.PAGE_NUMBER, source.getPageNumber());
            metadata.putIfAbsent(MetadataKeys.CLASS_NAME, source.getClassName());
            metadata.putIfAbsent(MetadataKeys.METHOD_NAME, source.getMethodName());
        }
        metadata.putIfAbsent(MetadataKeys.CHUNK_ID, id);
        return new Document(payload.getText(), metadata);
    }

    private RetrievedChunk map(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        ChunkSource source = new ChunkSource();
        if (metadata != null) {
            source.setFileName(asString(metadata.get(MetadataKeys.FILE_NAME)));
            source.setPageNumber(asInteger(metadata.get(MetadataKeys.PAGE_NUMBER)));
            source.setClassName(asString(metadata.get(MetadataKeys.CLASS_NAME)));
            source.setMethodName(asString(metadata.get(MetadataKeys.METHOD_NAME)));
        }
        String chunkId = metadata != null ? asString(metadata.get(MetadataKeys.CHUNK_ID)) : null;
        String resolvedId = chunkId != null ? chunkId : document.getId();
        double score = document.getScore() != null ? document.getScore() : 0.0;
        return new RetrievedChunk(resolvedId, document.getText(), score, source);
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
