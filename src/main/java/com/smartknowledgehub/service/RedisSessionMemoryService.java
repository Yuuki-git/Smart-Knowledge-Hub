package com.smartknowledgehub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartknowledgehub.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RedisSessionMemoryService implements SessionMemoryService {
    private static final Logger log = LoggerFactory.getLogger(RedisSessionMemoryService.class);
    // 会话消息在 Redis 中保留 12 小时
    private static final Duration TTL = Duration.ofHours(12);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisSessionMemoryService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void appendMessage(String sessionId, ChatMessage message) {
        String key = key(sessionId);
        try {
            String payload = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().rightPush(key, payload);
            redisTemplate.expire(key, TTL);
        } catch (JsonProcessingException e) {
            // 序列化失败时仅记录日志，不中断主流程
            log.warn("Failed to serialize message for session {}", sessionId, e);
        }
    }

    @Override
    public List<ChatMessage> recentMessages(String sessionId, int limit) {
        String key = key(sessionId);
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) {
            return Collections.emptyList();
        }
        long start = Math.max(0, size - limit);
        List<String> raw = redisTemplate.opsForList().range(key, start, size - 1);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        return raw.stream()
                .map(this::deserialize)
                .collect(Collectors.toList());
    }

    private ChatMessage deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, ChatMessage.class);
        } catch (JsonProcessingException e) {
            // 反序列化失败返回空消息占位
            log.warn("Failed to deserialize chat message payload", e);
            return new ChatMessage("system", "", null);
        }
    }

    private String key(String sessionId) {
        return "session:" + sessionId + ":messages";
    }
}
