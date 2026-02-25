package com.smartknowledgehub.service;

import com.smartknowledgehub.config.QueryRewriteProperties;
import com.smartknowledgehub.model.ModelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "app.rewrite", name = "enabled", havingValue = "true")
public class LlmQueryRewriteService implements QueryRewriteService {
    private static final Logger log = LoggerFactory.getLogger(LlmQueryRewriteService.class);
    private static final String SYSTEM_PROMPT = """
            You rewrite user questions into precise, search-friendly technical queries.
            Preserve the original language.
            Output only the rewritten query with no extra text.
            """;

    private final LlmRouter llmRouter;
    private final QueryRewriteProperties properties;

    public LlmQueryRewriteService(LlmRouter llmRouter, QueryRewriteProperties properties) {
        this.llmRouter = llmRouter;
        this.properties = properties;
    }

    @Override
    public String rewrite(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }
        Optional<ChatClient> chatClientOpt = llmRouter.resolve(resolveProvider());
        if (chatClientOpt.isEmpty()) {
            return query;
        }
        try {
            String rewritten = chatClientOpt.get()
                    .prompt()
                    .system(SYSTEM_PROMPT)
                    .user(query)
                    .call()
                    .content();
            if (rewritten == null || rewritten.isBlank()) {
                return query;
            }
            return trimToMax(rewritten.trim());
        } catch (Exception ex) {
            log.warn("Query rewrite failed, fallback to original query.");
            return query;
        }
    }

    private ModelProvider resolveProvider() {
        ModelProvider provider = properties.getProvider();
        return provider == null ? ModelProvider.AUTO : provider;
    }

    private String trimToMax(String value) {
        int max = properties.getMaxLength();
        if (max <= 0 || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
