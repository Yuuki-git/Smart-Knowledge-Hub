package com.smartknowledgehub.service;

import com.smartknowledgehub.model.RetrievedChunk;

import java.util.List;

public interface KeywordSearchService {
    List<RetrievedChunk> search(String query, int topK);
}
