package com.devflow.infra.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j AI 模型配置
 */
@Slf4j
@Configuration
public class LangChain4jConfig {

    @Value("${langchain4j.open-ai.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.model-name}")
    private String modelName;

    @Value("${langchain4j.open-ai.temperature}")
    private double temperature;

    @Value("${langchain4j.open-ai.max-tokens}")
    private int maxTokens;

    @Value("${langchain4j.open-ai.timeout}")
    private Duration timeout;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("Initializing ChatLanguageModel: baseUrl={}, model={}", baseUrl, modelName);
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .build();
    }
}
