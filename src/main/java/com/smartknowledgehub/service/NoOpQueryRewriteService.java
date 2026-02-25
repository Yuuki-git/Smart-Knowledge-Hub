package com.smartknowledgehub.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.rewrite", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpQueryRewriteService implements QueryRewriteService {
    @Override
    public String rewrite(String query) {
        return query;
    }
}
