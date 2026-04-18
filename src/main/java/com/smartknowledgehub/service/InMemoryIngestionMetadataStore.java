package com.smartknowledgehub.service;

import com.smartknowledgehub.model.ChunkPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(prefix = "app.persistence.postgres", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryIngestionMetadataStore implements IngestionMetadataStore {
    private final Map<String, String> jobStatus = new ConcurrentHashMap<>();

    @Override
    public void createJob(String jobId, String documentId, String fileName, String storageUri) {
        jobStatus.put(jobId, "QUEUED");
    }

    @Override
    public void markJobProcessing(String jobId) {
        jobStatus.put(jobId, "PROCESSING");
    }

    @Override
    public void markJobIndexed(String jobId) {
        jobStatus.put(jobId, "INDEXED");
    }

    @Override
    public void markJobEmpty(String jobId) {
        jobStatus.put(jobId, "EMPTY");
    }

    @Override
    public void markJobFailed(String jobId, String errorMessage) {
        jobStatus.put(jobId, "FAILED");
    }

    @Override
    public Optional<String> findJobStatus(String jobId) {
        return Optional.ofNullable(jobStatus.get(jobId));
    }

    @Override
    public void saveDocument(String documentId, String fileName, String storageUri) {
        // 无 PostgreSQL 模式下不落库，仅保留任务状态
    }

    @Override
    public void saveChunks(String documentId, List<ChunkPayload> chunks) {
        // 无 PostgreSQL 模式下不落库，仅保留任务状态
    }
}
