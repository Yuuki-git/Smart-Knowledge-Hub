package com.smartknowledgehub.service;

import com.smartknowledgehub.model.ChatMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.persistence.postgres", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpConversationArchiveService implements ConversationArchiveService {
    @Override
    public void append(String sessionId, ChatMessage message) {
        // 默认不启用 PostgreSQL 会话归档
    }
}
