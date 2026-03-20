package com.smartknowledgehub.service;

import com.smartknowledgehub.model.ChatMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;

@Service
@ConditionalOnProperty(prefix = "app.persistence.postgres", name = "enabled", havingValue = "true")
public class PostgresConversationArchiveService implements ConversationArchiveService {
    private static final String UPSERT_CONVERSATION_SQL = """
            INSERT INTO skh_conversation(session_id, created_at, updated_at)
            VALUES (?, NOW(), NOW())
            ON CONFLICT (session_id) DO UPDATE
            SET updated_at = NOW()
            RETURNING id
            """;

    private static final String INSERT_MESSAGE_SQL = """
            INSERT INTO skh_message(conversation_id, role, content, created_at)
            VALUES (?, ?, ?, ?)
            """;

    private final PostgresJdbcSupport jdbcSupport;

    public PostgresConversationArchiveService(PostgresJdbcSupport jdbcSupport) {
        this.jdbcSupport = jdbcSupport;
    }

    @Override
    public void append(String sessionId, ChatMessage message) {
        if (sessionId == null || sessionId.isBlank() || message == null || message.getContent() == null) {
            return;
        }
        jdbcSupport.transaction("archive conversation message", connection -> {
            long conversationId = upsertConversation(connection, sessionId);
            insertMessage(connection, conversationId, message);
        });
    }

    private long upsertConversation(java.sql.Connection connection, String sessionId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_CONVERSATION_SQL)) {
            statement.setString(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
        }
        throw new IllegalStateException("Failed to load conversation id for session: " + sessionId);
    }

    private void insertMessage(java.sql.Connection connection, long conversationId, ChatMessage message) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_MESSAGE_SQL)) {
            statement.setLong(1, conversationId);
            statement.setString(2, normalizeRole(message.getRole()));
            statement.setString(3, message.getContent());
            bindCreatedAt(statement, message.getTimestamp());
            statement.executeUpdate();
        }
    }

    private void bindCreatedAt(PreparedStatement statement, Instant timestamp) throws Exception {
        if (timestamp == null) {
            statement.setNull(4, Types.TIMESTAMP_WITH_TIMEZONE);
            return;
        }
        statement.setTimestamp(4, Timestamp.from(timestamp));
    }

    private String normalizeRole(String role) {
        return switch (role == null ? "" : role.toLowerCase()) {
            case "assistant" -> "assistant";
            case "system" -> "system";
            default -> "user";
        };
    }
}
