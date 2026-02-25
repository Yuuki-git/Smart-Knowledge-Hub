package com.smartknowledgehub.service;

import com.smartknowledgehub.model.RetrievedChunk;

import java.util.List;

public interface RetrievalService {
    List<RetrievedChunk> retrieve(String query, int topK);
}
