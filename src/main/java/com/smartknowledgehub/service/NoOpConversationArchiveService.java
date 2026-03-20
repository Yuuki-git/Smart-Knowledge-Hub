package com.smartknowledgehub.service;

import com.smartknowledgehub.model.ChatMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(ConversationArchiveService.class)
public class NoOpConversationArchiveService implements ConversationArchiveService {
    @Override
    public void append(String sessionId, ChatMessage message) {
        // 默认不启用 PostgreSQL 会话归档
    }
}
