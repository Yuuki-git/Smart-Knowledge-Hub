package com.smartknowledgehub.service;

import com.smartknowledgehub.model.ChunkPayload;

import java.util.List;

public interface KeywordIndexService {
    void index(List<ChunkPayload> chunks);
}
