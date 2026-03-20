package com.smartknowledgehub.service;

import com.smartknowledgehub.model.RetrievedChunk;
import com.smartknowledgehub.model.RetrievalScope;

import java.util.List;

public interface RetrievalService {
    default List<RetrievedChunk> retrieve(String query, int topK) {
        return retrieve(query, topK, null);
    }

    List<RetrievedChunk> retrieve(String query, int topK, RetrievalScope scope);
}
