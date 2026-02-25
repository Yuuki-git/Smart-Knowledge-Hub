package com.smartknowledgehub.service;

import com.smartknowledgehub.model.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class StubKeywordSearchService implements KeywordSearchService {
    private static final Logger log = LoggerFactory.getLogger(StubKeywordSearchService.class);

    @Override
    public List<RetrievedChunk> search(String query, int topK) {
        log.debug("Keyword search stub called: query='{}', topK={}", query, topK);
        return Collections.emptyList();
    }
}
