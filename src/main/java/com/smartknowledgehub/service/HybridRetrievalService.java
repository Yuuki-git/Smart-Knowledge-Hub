package com.smartknowledgehub.service;

import com.smartknowledgehub.config.RetrievalProperties;
import com.smartknowledgehub.model.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class HybridRetrievalService implements RetrievalService {
    private final VectorSearchService vectorSearchService;
    private final KeywordSearchService keywordSearchService;
    private final RetrievalProperties properties;

    public HybridRetrievalService(VectorSearchService vectorSearchService,
                                  KeywordSearchService keywordSearchService,
                                  RetrievalProperties properties) {
        this.vectorSearchService = vectorSearchService;
        this.keywordSearchService = keywordSearchService;
        this.properties = properties;
    }

    @Override
    public List<RetrievedChunk> retrieve(String query, int topK) {
        int resolvedTopK = topK > 0 ? topK : properties.getTopK();
        List<RetrievedChunk> vectorResults = vectorSearchService.search(query, resolvedTopK);
        List<RetrievedChunk> keywordResults = keywordSearchService.search(query, resolvedTopK);
        if (keywordResults.isEmpty()) {
            return vectorResults;
        }
        if (vectorResults.isEmpty()) {
            return keywordResults;
        }
        return fuseRrf(vectorResults, keywordResults, resolvedTopK, properties.getRrfK());
    }

    private List<RetrievedChunk> fuseRrf(List<RetrievedChunk> vectorResults,
                                         List<RetrievedChunk> keywordResults,
                                         int topK,
                                         int rrfK) {
        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
        Map<String, Double> scores = new HashMap<>();

        applyRrf(vectorResults, merged, scores, rrfK);
        applyRrf(keywordResults, merged, scores, rrfK);

        return scores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(topK)
                .map(entry -> {
                    RetrievedChunk chunk = merged.get(entry.getKey());
                    chunk.setScore(entry.getValue());
                    return chunk;
                })
                .collect(Collectors.toList());
    }

    private void applyRrf(List<RetrievedChunk> results,
                          Map<String, RetrievedChunk> merged,
                          Map<String, Double> scores,
                          int rrfK) {
        for (int i = 0; i < results.size(); i++) {
            RetrievedChunk chunk = results.get(i);
            String key = resolveKey(chunk, i);
            merged.putIfAbsent(key, chunk);
            double score = 1.0 / (rrfK + i + 1);
            scores.merge(key, score, Double::sum);
        }
    }

    private String resolveKey(RetrievedChunk chunk, int index) {
        if (chunk.getId() != null && !chunk.getId().isBlank()) {
            return chunk.getId();
        }
        return Integer.toHexString(Objects.hash(chunk.getText(), chunk.getSource(), index));
    }
}
