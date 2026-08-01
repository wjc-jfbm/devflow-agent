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
 *
 * 高并发要点：
 * - LangChain4j 1.0.0-beta2 使用 JDK HttpClient 作为 HTTP 层
 * - OpenAiChatModel 是线程安全的（内部使用 JDK HttpClient，天然线程安全）
 * - 单个 ChatLanguageModel Bean 被所有 6 个 Agent + review 线程共享，无需多实例
 * - JDK HttpClient 默认每个目标 5 个连接，通过 JVM 参数调整：
 *   -Djdk.httpclient.connectionPoolSize=50（已配置在 Dockerfile JAVA_OPTS 中）
 * - 匹配 MQ 消费者并发数 + review 并行度，避免连接池成为瓶颈
 *
 * 注意：非 Docker 环境（IDE 本地调试）需手动设置连接池参数，
 * 否则使用 JVM 默认值 5 个连接会成为吞吐量瓶颈。
 * IDE 中可在 VM options 添加: -Djdk.httpclient.connectionPoolSize=50
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

    @Value("${langchain4j.open-ai.max-retries:2}")
    private int maxRetries;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("Initializing ChatLanguageModel: baseUrl={}, model={}, maxRetries={}, timeout={}",
                baseUrl, modelName, maxRetries, timeout);
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
