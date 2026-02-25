package com.smartknowledgehub.service;

import com.smartknowledgehub.model.ChatChunk;
import com.smartknowledgehub.model.ChatMessage;
import com.smartknowledgehub.model.ChatRequest;
import com.smartknowledgehub.model.Citation;
import com.smartknowledgehub.model.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String SYSTEM_PROMPT = """
            You are a senior Java architect.
            Only answer based on the provided Context.
            If the Context does not contain the answer, say: "Not found in the uploaded documents."
            Always include citations with file/class/page references.
            """;

    private final RetrievalService retrievalService;
    private final LlmRouter llmRouter;
    private final SessionMemoryService sessionMemoryService;
    private final QueryRewriteService queryRewriteService;
    private final CitationMapper citationMapper = new CitationMapper();

    public ChatService(RetrievalService retrievalService,
                       LlmRouter llmRouter,
                       SessionMemoryService sessionMemoryService,
                       QueryRewriteService queryRewriteService) {
        this.retrievalService = retrievalService;
        this.llmRouter = llmRouter;
        this.sessionMemoryService = sessionMemoryService;
        this.queryRewriteService = queryRewriteService;
    }

    public Flux<ServerSentEvent<ChatChunk>> stream(ChatRequest request) {
        String rewrittenQuery = queryRewriteService.rewrite(request.getQuestion());
        List<RetrievedChunk> context = retrievalService.retrieve(rewrittenQuery, request.getTopK());
        if (context.isEmpty()) {
            ChatChunk finalChunk = ChatChunk.finalChunk("Not found in the uploaded documents.", List.of());
            return Flux.just(ServerSentEvent.builder(finalChunk).event("final").build());
        }

        sessionMemoryService.appendMessage(request.getSessionId(),
                new ChatMessage("user", request.getQuestion(), Instant.now()));

        Optional<ChatClient> chatClientOpt = llmRouter.resolve(request.getModelProvider());
        if (chatClientOpt.isEmpty()) {
            ChatChunk finalChunk = ChatChunk.finalChunk("No LLM provider is configured.", List.of());
            return Flux.just(ServerSentEvent.builder(finalChunk).event("final").build());
        }

        String contextBlock = buildContext(context);
        StringJoiner system = new StringJoiner("\n\n");
        system.add(SYSTEM_PROMPT);
        system.add("[Context]");
        system.add(contextBlock);

        AtomicReference<StringBuilder> answer = new AtomicReference<>(new StringBuilder());
        Flux<String> content = chatClientOpt.get()
                .prompt()
                .system(system.toString())
                .user(request.getQuestion())
                .stream()
                .content()
                .doOnNext(token -> answer.get().append(token));

        List<Citation> citations = citationMapper.toCitations(context);

        Flux<ServerSentEvent<ChatChunk>> deltas = content.map(token ->
                ServerSentEvent.builder(ChatChunk.delta(token)).event("delta").build());

        Mono<ServerSentEvent<ChatChunk>> finalEvent = Mono.fromSupplier(() -> {
            String finalAnswer = answer.get().toString();
            sessionMemoryService.appendMessage(
                    request.getSessionId(),
                    new ChatMessage("assistant", finalAnswer, Instant.now())
            );
            return ServerSentEvent.builder(ChatChunk.finalChunk(finalAnswer, citations)).event("final").build();
        });

        return deltas.concatWith(finalEvent);
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
}
