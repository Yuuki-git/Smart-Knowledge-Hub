package com.smartknowledgehub.service;

import com.smartknowledgehub.config.PostgresPersistenceProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(prefix = "app.persistence.postgres", name = "enabled", havingValue = "true")
public class PostgresJdbcSupport {
    private static final String[] DDL = {
            """
            CREATE TABLE IF NOT EXISTS skh_document (
                id VARCHAR(64) PRIMARY KEY,
                name TEXT NOT NULL,
                file_type VARCHAR(32),
                uploaded_by VARCHAR(128),
                storage_uri TEXT,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS skh_chunk (
                id VARCHAR(64) PRIMARY KEY,
                document_id VARCHAR(64) NOT NULL REFERENCES skh_document(id) ON DELETE CASCADE,
                chunk_index INTEGER NOT NULL,
                content TEXT NOT NULL,
                file_name TEXT,
                page_number INTEGER,
                class_name TEXT,
                method_name TEXT,
                attributes_json TEXT,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_skh_chunk_document ON skh_chunk(document_id)",
            """
            CREATE TABLE IF NOT EXISTS skh_ingestion_job (
                id VARCHAR(64) PRIMARY KEY,
                document_id VARCHAR(64) NOT NULL,
                file_name TEXT,
                storage_uri TEXT,
                status VARCHAR(32) NOT NULL,
                error_message TEXT,
                started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                finished_at TIMESTAMPTZ
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_skh_ingestion_document ON skh_ingestion_job(document_id)",
            """
            CREATE TABLE IF NOT EXISTS skh_conversation (
                id BIGSERIAL PRIMARY KEY,
                session_id VARCHAR(128) NOT NULL UNIQUE,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS skh_message (
                id BIGSERIAL PRIMARY KEY,
                conversation_id BIGINT NOT NULL REFERENCES skh_conversation(id) ON DELETE CASCADE,
                role VARCHAR(32) NOT NULL,
                content TEXT NOT NULL,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_skh_message_conversation ON skh_message(conversation_id)"
    };

    private final PostgresPersistenceProperties properties;
    private final AtomicBoolean schemaInitialized = new AtomicBoolean(false);

    public PostgresJdbcSupport(PostgresPersistenceProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        ensureSchema();
    }

    public <T> T query(String operation, SqlFunction<Connection, T> function) {
        ensureSchema();
        try (Connection connection = openConnection()) {
            return function.apply(connection);
        } catch (Exception ex) {
            throw new IllegalStateException("PostgreSQL operation failed: " + operation, ex);
        }
    }

    public void execute(String operation, SqlConsumer<Connection> consumer) {
        query(operation, connection -> {
            consumer.accept(connection);
            return null;
        });
    }

    public void transaction(String operation, SqlConsumer<Connection> consumer) {
        query(operation, connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                consumer.accept(connection);
                connection.commit();
                return null;
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        });
    }

    private void ensureSchema() {
        if (schemaInitialized.get() || !properties.isInitSchema()) {
            schemaInitialized.set(true);
            return;
        }
        synchronized (this) {
            if (schemaInitialized.get()) {
                return;
            }
            try (Connection connection = openConnection();
                 Statement statement = connection.createStatement()) {
                for (String ddl : DDL) {
                    statement.execute(ddl);
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("PostgreSQL operation failed: initialize schema", ex);
            }
            schemaInitialized.set(true);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                properties.getUrl(),
                properties.getUsername(),
                properties.getPassword()
        );
    }

    @FunctionalInterface
    public interface SqlFunction<T, R> {
        R apply(T input) throws Exception;
    }

    @FunctionalInterface
    public interface SqlConsumer<T> {
        void accept(T input) throws Exception;
    }
}
