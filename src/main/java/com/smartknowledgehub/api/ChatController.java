package com.smartknowledgehub.api;

import com.smartknowledgehub.model.ChatChunk;
import com.smartknowledgehub.model.ChatRequest;
import com.smartknowledgehub.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/chat")
    public Map<String, Object> chatUsage() {
        return Map.of(
                "message", "Use POST /api/chat with JSON body to stream SSE response.",
                "contentType", "application/json",
                "accept", "text/event-stream",
                "exampleBody", Map.of(
                        "sessionId", "demo-session",
                        "question", "Spring Cloud Nacos how to configure cluster?",
                        "modelProvider", "AUTO",
                        "topK", 5
                )
        );
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatChunk>> chat(@Valid @RequestBody ChatRequest request) {
        // SSE 流式输出答案
        return chatService.stream(request);
    }
}
