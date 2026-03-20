package com.smartknowledgehub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartknowledgehub.model.ChunkPayload;
import com.smartknowledgehub.model.ChunkSource;
import com.smartknowledgehub.model.MetadataKeys;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "app.persistence.postgres", name = "enabled", havingValue = "true")
public class PostgresIngestionMetadataStore implements IngestionMetadataStore {
    private static final int MAX_ERROR_LENGTH = 2000;

    private static final String UPSERT_JOB_SQL = """
            INSERT INTO skh_ingestion_job(id, document_id, file_name, storage_uri, status, started_at, updated_at)
            VALUES (?, ?, ?, ?, ?, NOW(), NOW())
            ON CONFLICT (id) DO UPDATE
            SET status = EXCLUDED.status,
                file_name = EXCLUDED.file_name,
                storage_uri = EXCLUDED.storage_uri,
                updated_at = NOW()
            """;

    private static final String UPDATE_JOB_STATUS_SQL = """
            UPDATE skh_ingestion_job
            SET status = ?, error_message = ?, updated_at = NOW(), finished_at = ?
            WHERE id = ?
            """;

    private static final String FIND_JOB_STATUS_SQL = "SELECT status FROM skh_ingestion_job WHERE id = ?";

    private static final String UPSERT_DOCUMENT_SQL = """
            INSERT INTO skh_document(id, name, file_type, uploaded_by, storage_uri, created_at)
            VALUES (?, ?, ?, ?, ?, NOW())
            ON CONFLICT (id) DO UPDATE
            SET name = EXCLUDED.name,
                file_type = EXCLUDED.file_type,
                storage_uri = EXCLUDED.storage_uri
            """;

    private static final String UPSERT_CHUNK_SQL = """
            INSERT INTO skh_chunk(
                id, document_id, chunk_index, content,
                file_name, page_number, class_name, method_name, attributes_json, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (id) DO UPDATE
            SET content = EXCLUDED.content,
                file_name = EXCLUDED.file_name,
                page_number = EXCLUDED.page_number,
                class_name = EXCLUDED.class_name,
                method_name = EXCLUDED.method_name,
                attributes_json = EXCLUDED.attributes_json
            """;

    private final PostgresJdbcSupport jdbcSupport;
    private final ObjectMapper objectMapper;

    public PostgresIngestionMetadataStore(PostgresJdbcSupport jdbcSupport, ObjectMapper objectMapper) {
        this.jdbcSupport = jdbcSupport;
        this.objectMapper = objectMapper;
    }

    @Override
    public void createJob(String jobId, String documentId, String fileName, String storageUri) {
        jdbcSupport.execute("create ingestion job", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_JOB_SQL)) {
                statement.setString(1, jobId);
                statement.setString(2, documentId);
                statement.setString(3, fileName);
                statement.setString(4, storageUri);
                statement.setString(5, "QUEUED");
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void markJobProcessing(String jobId) {
        updateJobStatus(jobId, "PROCESSING", null);
    }

    @Override
    public void markJobIndexed(String jobId) {
        updateJobStatus(jobId, "INDEXED", null);
    }

    @Override
    public void markJobEmpty(String jobId) {
        updateJobStatus(jobId, "EMPTY", null);
    }

    @Override
    public void markJobFailed(String jobId, String errorMessage) {
        updateJobStatus(jobId, "FAILED", errorMessage);
    }

    @Override
    public Optional<String> findJobStatus(String jobId) {
        return jdbcSupport.query("find ingestion job status", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(FIND_JOB_STATUS_SQL)) {
                statement.setString(1, jobId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.ofNullable(resultSet.getString("status")) : Optional.empty();
                }
            }
        });
    }

    @Override
    public void saveDocument(String documentId, String fileName, String storageUri) {
        jdbcSupport.execute("save document", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_DOCUMENT_SQL)) {
                statement.setString(1, documentId);
                statement.setString(2, fileName);
                statement.setString(3, extensionOf(fileName));
                statement.setString(4, "system");
                statement.setString(5, storageUri);
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void saveChunks(String documentId, List<ChunkPayload> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        jdbcSupport.transaction("save chunks", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_CHUNK_SQL)) {
                for (ChunkPayload chunk : chunks) {
                    bindChunk(statement, documentId, chunk);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
    }

    private void updateJobStatus(String jobId, String status, String errorMessage) {
        jdbcSupport.execute("update ingestion job status", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_JOB_STATUS_SQL)) {
                statement.setString(1, status);
                statement.setString(2, shorten(errorMessage));
                bindFinishedAt(statement, status);
                statement.setString(4, jobId);
                statement.executeUpdate();
            }
        });
    }

    private void bindChunk(PreparedStatement statement, String documentId, ChunkPayload chunk) throws Exception {
        ChunkSource source = chunk.getSource();
        statement.setString(1, chunk.getId());
        statement.setString(2, documentId);
        statement.setInt(3, chunkIndexOf(chunk.getAttributes()));
        statement.setString(4, chunk.getText());
        statement.setString(5, source != null ? source.getFileName() : null);
        setNullableInteger(statement, 6, source != null ? source.getPageNumber() : null);
        statement.setString(7, source != null ? source.getClassName() : null);
        statement.setString(8, source != null ? source.getMethodName() : null);
        statement.setString(9, toJson(chunk.getAttributes()));
    }

    private void bindFinishedAt(PreparedStatement statement, String status) throws Exception {
        if (isTerminalStatus(status)) {
            statement.setTimestamp(3, Timestamp.from(Instant.now()));
            return;
        }
        statement.setNull(3, Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private void setNullableInteger(PreparedStatement statement, int index, Integer value) throws Exception {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
            return;
        }
        statement.setInt(index, value);
    }

    private int chunkIndexOf(Map<String, Object> attributes) {
        if (attributes == null) {
            return 0;
        }
        Object value = attributes.get(MetadataKeys.CHUNK_INDEX);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String shorten(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return null;
        }
        return errorMessage.length() <= MAX_ERROR_LENGTH
                ? errorMessage
                : errorMessage.substring(0, MAX_ERROR_LENGTH);
    }

    private String toJson(Map<String, Object> attributes) {
        try {
            return objectMapper.writeValueAsString(attributes == null ? Map.of() : attributes);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private boolean isTerminalStatus(String status) {
        return switch (status) {
            case "INDEXED", "EMPTY", "FAILED" -> true;
            default -> false;
        };
    }
}
