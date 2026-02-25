package com.smartknowledgehub.api;

import com.smartknowledgehub.model.SearchRequest;
import com.smartknowledgehub.model.SearchResponse;
import com.smartknowledgehub.service.RetrievalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SearchController {
    private final RetrievalService retrievalService;

    public SearchController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping("/search")
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        return new SearchResponse(retrievalService.retrieve(request.getQuery(), request.getTopK()));
    }
}
