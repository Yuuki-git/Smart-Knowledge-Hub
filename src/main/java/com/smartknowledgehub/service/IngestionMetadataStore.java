package com.smartknowledgehub.service;

import com.smartknowledgehub.model.ChunkPayload;

import java.util.List;
import java.util.Optional;

public interface IngestionMetadataStore {
    void createJob(String jobId, String documentId, String fileName, String storageUri);

    void markJobProcessing(String jobId);

    void markJobIndexed(String jobId);

    void markJobEmpty(String jobId);

    void markJobFailed(String jobId, String errorMessage);

    Optional<String> findJobStatus(String jobId);

    void saveDocument(String documentId, String fileName, String storageUri);

    void saveChunks(String documentId, List<ChunkPayload> chunks);
}
