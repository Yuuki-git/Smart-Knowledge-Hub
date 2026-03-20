package com.smartknowledgehub.service;

import com.smartknowledgehub.model.RetrievedChunk;
import com.smartknowledgehub.model.RetrievalScope;

import java.util.List;

public interface KeywordSearchService {
    default List<RetrievedChunk> search(String query, int topK) {
        return search(query, topK, null);
    }

    List<RetrievedChunk> search(String query, int topK, RetrievalScope scope);
}
