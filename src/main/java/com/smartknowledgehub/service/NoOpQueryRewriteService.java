package com.smartknowledgehub.service;

import org.springframework.stereotype.Service;

@Service
public class NoOpQueryRewriteService implements QueryRewriteService {
    @Override
    public String rewrite(String query) {
        return query;
    }
}
