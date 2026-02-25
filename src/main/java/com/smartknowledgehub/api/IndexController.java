package com.smartknowledgehub.api;

import com.smartknowledgehub.model.IndexRequest;
import com.smartknowledgehub.service.VectorSearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class IndexController {
    private final VectorSearchService vectorSearchService;

    public IndexController(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    @PostMapping("/index")
    public Mono<Map<String, Object>> index(@Valid @RequestBody IndexRequest request) {
        vectorSearchService.index(request.getChunks());
        return Mono.just(Map.of("indexed", request.getChunks().size()));
    }
}
