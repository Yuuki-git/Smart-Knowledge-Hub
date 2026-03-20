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
import java.util.UUID;

@Service
public class IngestionService {
    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final Path storageRoot = Paths.get("data", "uploads");
    private final DocumentChunkingService documentChunkingService;
    private final VectorSearchService vectorSearchService;
    private final KeywordIndexService keywordIndexService;
    private final IngestionMetadataStore metadataStore;

    public IngestionService(DocumentChunkingService documentChunkingService,
                            VectorSearchService vectorSearchService,
                            KeywordIndexService keywordIndexService,
                            IngestionMetadataStore metadataStore) {
        this.documentChunkingService = documentChunkingService;
        this.vectorSearchService = vectorSearchService;
        this.keywordIndexService = keywordIndexService;
        this.metadataStore = metadataStore;
    }

    public Mono<UploadResponse> ingest(FilePart filePart) {
        String documentId = UUID.randomUUID().toString();
        String jobId = UUID.randomUUID().toString();
        Path target = storageRoot.resolve(documentId + "-" + sanitize(filePart.filename()));
        metadataStore.createJob(jobId, documentId, filePart.filename(), target.toString());

        // IO 与解析走阻塞线程池，避免占用事件循环
        return Mono.fromCallable(() -> Files.createDirectories(storageRoot))
                .subscribeOn(Schedulers.boundedElastic())
                .then(filePart.transferTo(target))
                .then(Mono.fromCallable(() -> processFile(documentId, jobId, target, filePart.filename()))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    public Mono<String> status(String jobId) {
        return Mono.justOrEmpty(metadataStore.findJobStatus(jobId));
    }

    private String sanitize(String filename) {
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private UploadResponse processFile(String documentId, String jobId, Path path, String originalName) {
        // 在边界统一处理异常，底层链路按正常路径执行
        try {
            metadataStore.markJobProcessing(jobId);
            List<ChunkPayload> chunks = documentChunkingService.chunk(path, documentId, originalName);
            metadataStore.saveDocument(documentId, originalName, path.toAbsolutePath().toString());
            if (chunks.isEmpty()) {
                metadataStore.markJobEmpty(jobId);
                return new UploadResponse(documentId, jobId, "EMPTY");
            }
            metadataStore.saveChunks(documentId, chunks);
            vectorSearchService.index(chunks);
            keywordIndexService.index(chunks);
            metadataStore.markJobIndexed(jobId);
            log.info("Indexed document {} ({} chunks) at {}", documentId, chunks.size(), Instant.now());
            return new UploadResponse(documentId, jobId, "INDEXED");
        } catch (Exception ex) {
            metadataStore.markJobFailed(jobId, ex.getMessage());
            log.warn("Failed ingestion for document {}", documentId, ex);
            return new UploadResponse(documentId, jobId, "FAILED");
        }
    }
}
