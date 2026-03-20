package com.smartknowledgehub.service;

import com.smartknowledgehub.model.RetrievedChunk;
import com.smartknowledgehub.model.RetrievalScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "app.search", name = "enabled", havingValue = "false", matchIfMissing = true)
public class StubKeywordSearchService implements KeywordSearchService {
    private static final Logger log = LoggerFactory.getLogger(StubKeywordSearchService.class);

    @Override
    public List<RetrievedChunk> search(String query, int topK, RetrievalScope scope) {
        log.debug("Keyword search stub called: query='{}', topK={}, scope={}", query, topK, scope);
        return Collections.emptyList();
    }
}
