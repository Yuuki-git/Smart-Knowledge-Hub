package com.smartknowledgehub.service;

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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IngestionService {
    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final Path storageRoot = Paths.get("data", "uploads");
    private final Map<String, String> jobStatus = new ConcurrentHashMap<>();

    public Mono<UploadResponse> ingest(FilePart filePart) {
        String documentId = UUID.randomUUID().toString();
        String jobId = UUID.randomUUID().toString();
        jobStatus.put(jobId, "QUEUED");

        Path target = storageRoot.resolve(documentId + "-" + sanitize(filePart.filename()));
        return Mono.fromCallable(() -> Files.createDirectories(storageRoot))
                .subscribeOn(Schedulers.boundedElastic())
                .then(filePart.transferTo(target))
                .then(Mono.fromSupplier(() -> {
                    jobStatus.put(jobId, "STORED");
                    log.info("Stored upload {} at {}", documentId, target);
                    return new UploadResponse(documentId, jobId, "STORED");
                }));
    }

    public Mono<String> status(String jobId) {
        return Mono.justOrEmpty(jobStatus.get(jobId));
    }

    private String sanitize(String filename) {
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
