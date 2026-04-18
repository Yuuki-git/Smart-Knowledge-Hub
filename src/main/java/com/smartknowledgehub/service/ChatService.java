package com.smartknowledgehub.service;

import com.smartknowledgehub.model.ChatChunk;
import com.smartknowledgehub.model.ChatMessage;
import com.smartknowledgehub.model.ChatRequest;
import com.smartknowledgehub.model.Citation;
import com.smartknowledgehub.model.RetrievedChunk;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ChatService {
    private static final String NOT_FOUND_REPLY = "Not found in the uploaded documents.";
    private static final String NO_PROVIDER_REPLY = "No LLM provider is configured.";
    // Keep a short sliding window of history messages.
    private static final int HISTORY_LIMIT = 12;
    // History can disambiguate intent, but facts must come from retrieved context.
    private static final String SYSTEM_PROMPT = """
            您是一位资深的 Java 架构师。
            对话历史仅可用于意图消歧。
            仅根据提供的上下文进行回答。
            如果上下文中不包含答案，请说：“未在上传的文档中找到。”
            始终附上文件/类/页面引用。
            """;

    private final RetrievalService retrievalService;
    private final LlmRouter llmRouter;
    private final SessionMemoryService sessionMemoryService;
    private final QueryRewriteService queryRewriteService;
    private final AnswerGroundingValidator answerGroundingValidator;
    private final CitationMapper citationMapper = new CitationMapper();

    public ChatService(RetrievalService retrievalService,
                       LlmRouter llmRouter,
                       SessionMemoryService sessionMemoryService,
                       QueryRewriteService queryRewriteService,
                       AnswerGroundingValidator answerGroundingValidator) {
        this.retrievalService = retrievalService;
        this.llmRouter = llmRouter;
        this.sessionMemoryService = sessionMemoryService;
        this.queryRewriteService = queryRewriteService;
        this.answerGroundingValidator = answerGroundingValidator;
    }

    public Flux<ServerSentEvent<ChatChunk>> stream(ChatRequest request) {
        // Read history first, then append current user message.
        List<ChatMessage> history = sessionMemoryService.recentMessages(request.getSessionId(), HISTORY_LIMIT);
        sessionMemoryService.appendMessage(
                request.getSessionId(),
                new ChatMessage("user", request.getQuestion(), Instant.now())
        );

        String rewrittenQuery = queryRewriteService.rewrite(request.getQuestion());
        List<RetrievedChunk> context = retrievalService.retrieve(rewrittenQuery, request.getTopK(), request.getScope());
        if (context.isEmpty()) {
            return fallback(request.getSessionId(), NOT_FOUND_REPLY);
        }

        Optional<ChatClient> chatClientOpt = llmRouter.resolve(request.getModelProvider());
        if (chatClientOpt.isEmpty()) {
            return fallback(request.getSessionId(), NO_PROVIDER_REPLY);
        }

        String systemPrompt = buildSystemPrompt(history, context);
        AtomicReference<StringBuilder> answer = new AtomicReference<>(new StringBuilder());
        Flux<String> content = chatClientOpt.get()
                .prompt()
                .system(systemPrompt)
                .user(request.getQuestion())
                .stream()
                .content()
                .doOnNext(token -> answer.get().append(token));

        List<Citation> citations = citationMapper.toCitations(context);
        Flux<ServerSentEvent<ChatChunk>> deltas = content.map(token ->
                ServerSentEvent.builder(ChatChunk.delta(token)).event("delta").build());

        Mono<ServerSentEvent<ChatChunk>> finalEvent = Mono.fromSupplier(() -> {
            String finalAnswer = answer.get().toString();
            if (!answerGroundingValidator.hasGroundedClaim(finalAnswer, context)) {
                return finalEventWithMemory(request.getSessionId(), NOT_FOUND_REPLY, List.of());
            }
            return finalEventWithMemory(request.getSessionId(), finalAnswer, citations);
        });

        return deltas.concatWith(finalEvent);
    }

    private String buildSystemPrompt(List<ChatMessage> history, List<RetrievedChunk> context) {
        StringJoiner system = new StringJoiner("\n\n");
        system.add(SYSTEM_PROMPT);
        system.add("[Conversation History]");
        system.add(buildHistory(history));
        system.add("[Context]");
        system.add(buildContext(context));
        return system.toString();
    }

    private String buildHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "(empty)";
        }
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : history) {
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            builder.append(normalizeRole(message.getRole()))
                    .append(": ")
                    .append(message.getContent())
                    .append("\n");
        }
        return builder.isEmpty() ? "(empty)" : builder.toString();
    }

    private String normalizeRole(String role) {
        return switch (role == null ? "" : role.toLowerCase()) {
            case "assistant" -> "assistant";
            case "system" -> "system";
            default -> "user";
        };
    }

    private String buildContext(List<RetrievedChunk> context) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < context.size(); i++) {
            RetrievedChunk chunk = context.get(i);
            builder.append("[").append(i + 1).append("] ");
            builder.append(chunk.getText()).append("\n");
        }
        return builder.toString();
    }

    private Flux<ServerSentEvent<ChatChunk>> fallback(String sessionId, String reply) {
        return Flux.just(finalEventWithMemory(sessionId, reply, List.of()));
    }

    private ServerSentEvent<ChatChunk> finalEventWithMemory(String sessionId,
                                                            String answer,
                                                            List<Citation> citations) {
        sessionMemoryService.appendMessage(
                sessionId,
                new ChatMessage("assistant", answer, Instant.now())
        );
        return ServerSentEvent.builder(ChatChunk.finalChunk(answer, citations)).event("final").build();
    }
}
