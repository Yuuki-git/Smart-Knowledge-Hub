package com.smartknowledgehub.service;

import com.smartknowledgehub.model.ChatMessage;

public interface ConversationArchiveService {
    void append(String sessionId, ChatMessage message);
}
