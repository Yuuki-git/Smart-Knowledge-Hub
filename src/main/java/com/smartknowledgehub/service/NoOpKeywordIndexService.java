package com.smartknowledgehub.service;

import com.smartknowledgehub.model.ChunkPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "app.search", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpKeywordIndexService implements KeywordIndexService {
    @Override
    public void index(List<ChunkPayload> chunks) {
        // no-op
    }
}
