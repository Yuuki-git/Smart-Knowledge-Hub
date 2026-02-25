package com.smartknowledgehub.service;

import com.smartknowledgehub.model.ModelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LlmRouter {
    private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);

    // 已加载的模型客户端
    private final Map<ModelProvider, ChatClient> clients = new EnumMap<>(ModelProvider.class);

    public LlmRouter(ObjectProvider<OpenAiChatModel> openAiProvider,
                     ObjectProvider<DeepSeekChatModel> deepSeekProvider,
                     ObjectProvider<OllamaChatModel> ollamaProvider) {
        openAiProvider.ifAvailable(model -> clients.put(ModelProvider.OPENAI, buildClient(model)));
        deepSeekProvider.ifAvailable(model -> clients.put(ModelProvider.DEEPSEEK, buildClient(model)));
        ollamaProvider.ifAvailable(model -> clients.put(ModelProvider.OLLAMA, buildClient(model)));
        log.info("LLM providers available: {}", clients.keySet());
    }

    public Optional<ChatClient> resolve(ModelProvider provider) {
        if (provider == null || provider == ModelProvider.AUTO) {
            return defaultClient();
        }
        ChatClient client = clients.get(provider);
        return Optional.ofNullable(client);
    }

    private Optional<ChatClient> defaultClient() {
        // 按优先级选择默认模型，避免多段 if/else
        for (ModelProvider provider : List.of(ModelProvider.DEEPSEEK, ModelProvider.OPENAI, ModelProvider.OLLAMA)) {
            ChatClient client = clients.get(provider);
            if (client != null) {
                return Optional.of(client);
            }
        }
        return Optional.empty();
    }

    private ChatClient buildClient(ChatModel model) {
        return ChatClient.builder(model).build();
    }
}
