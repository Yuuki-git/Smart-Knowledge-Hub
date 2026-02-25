package com.smartknowledgehub.service;

import com.smartknowledgehub.model.ChunkPayload;
import com.smartknowledgehub.model.UploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IngestionService {
    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final Path storageRoot = Paths.get("data", "uploads");
    private final Map<String, String> jobStatus = new ConcurrentHashMap<>();
    private final DocumentChunkingService documentChunkingService;
    private final VectorSearchService vectorSearchService;
    private final KeywordIndexService keywordIndexService;

    public IngestionService(DocumentChunkingService documentChunkingService,
                            VectorSearchService vectorSearchService,
                            KeywordIndexService keywordIndexService) {
        this.documentChunkingService = documentChunkingService;
        this.vectorSearchService = vectorSearchService;
        this.keywordIndexService = keywordIndexService;
    }

    public Mono<UploadResponse> ingest(FilePart filePart) {
        String documentId = UUID.randomUUID().toString();
        String jobId = UUID.randomUUID().toString();
        jobStatus.put(jobId, "QUEUED");

        Path target = storageRoot.resolve(documentId + "-" + sanitize(filePart.filename()));
        return Mono.fromCallable(() -> Files.createDirectories(storageRoot))
                .subscribeOn(Schedulers.boundedElastic())
                .then(filePart.transferTo(target))
                .then(Mono.fromCallable(() -> processFile(documentId, jobId, target, filePart.filename()))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    public Mono<String> status(String jobId) {
        return Mono.justOrEmpty(jobStatus.get(jobId));
    }

    private String sanitize(String filename) {
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private UploadResponse processFile(String documentId, String jobId, Path path, String originalName) {
        try {
            jobStatus.put(jobId, "PROCESSING");
            List<ChunkPayload> chunks = documentChunkingService.chunk(path, documentId, originalName);
            if (chunks.isEmpty()) {
                jobStatus.put(jobId, "EMPTY");
                return new UploadResponse(documentId, jobId, "EMPTY");
            }
            vectorSearchService.index(chunks);
            keywordIndexService.index(chunks);
            jobStatus.put(jobId, "INDEXED");
            log.info("Indexed document {} ({} chunks) at {}", documentId, chunks.size(), Instant.now());
            return new UploadResponse(documentId, jobId, "INDEXED");
        } catch (Exception ex) {
            jobStatus.put(jobId, "FAILED");
            log.warn("Failed ingestion for document {}", documentId, ex);
            return new UploadResponse(documentId, jobId, "FAILED");
        }
    }
}
