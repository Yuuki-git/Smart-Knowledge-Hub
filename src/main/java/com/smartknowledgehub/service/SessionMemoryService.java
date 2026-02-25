package com.smartknowledgehub.service;

import com.smartknowledgehub.model.ChatMessage;

import java.util.List;

public interface SessionMemoryService {
    void appendMessage(String sessionId, ChatMessage message);

    List<ChatMessage> recentMessages(String sessionId, int limit);
}
