package com.smartknowledgehub.model;

import java.util.List;

public class SearchResponse {
    private List<RetrievedChunk> results;

    public SearchResponse() {
    }

    public SearchResponse(List<RetrievedChunk> results) {
        this.results = results;
    }

    public List<RetrievedChunk> getResults() {
        return results;
    }

    public void setResults(List<RetrievedChunk> results) {
        this.results = results;
    }
}
