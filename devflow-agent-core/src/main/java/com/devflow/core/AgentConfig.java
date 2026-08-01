package com.devflow.core;

import com.devflow.core.agent.*;
import com.devflow.core.orchestration.SupervisorAgent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Agent Bean 配置 + 线程池管理
 *
 * 线程池设计：
 * - reviewExecutor: 审查阶段 3 个维度并行 × 10 个并发任务 = core 10, max 20
 * - asyncLogExecutor: Agent 执行日志异步写入，解耦 LLM 主流程与 DB 日志
 * - 统一由 Spring 管理生命周期，应用关闭时优雅终止（waitForJobsToCompleteOnShutdown）
 */
@Configuration
public class AgentConfig {

    /**
     * Review 阶段专用线程池 — 替代原先的 Executors.newFixedThreadPool(3)
     *
     * 原先问题：3 个固定线程共享给所有任务，高并发下 review 互相阻塞。
     * 现在：最多 20 个线程，支撑 6+ 个任务同时做 review。
     */
    @Bean("reviewExecutor")
    public ThreadPoolTaskExecutor reviewExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("review-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Agent 执行日志异步写入线程池
     *
     * 将 AgentExecution 的 save/update 操作从 LLM 调用主线程中剥离，
     * 减少每次 Agent 调用的 DB 往返延迟（每条流水线节省 6-12 次同步 DB 写入）。
     * 单线程 + 无界队列：保证日志写入顺序，避免高并发下数据库连接竞争。
     */
    @Bean("asyncLogExecutor")
    public ThreadPoolTaskExecutor asyncLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-log-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    public RequirementsAgent requirementsAgent(ChatLanguageModel chatModel) {
        return AiServices.builder(RequirementsAgent.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    public ArchitectAgent architectAgent(ChatLanguageModel chatModel) {
        return AiServices.builder(ArchitectAgent.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    public CoderAgent coderAgent(ChatLanguageModel chatModel) {
        return AiServices.builder(CoderAgent.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    public TesterAgent testerAgent(ChatLanguageModel chatModel) {
        return AiServices.builder(TesterAgent.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    public ReviewerAgent reviewerAgent(ChatLanguageModel chatModel) {
        return AiServices.builder(ReviewerAgent.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    public SupervisorAgent supervisorAgent(ChatLanguageModel chatModel) {
        return new SupervisorAgent(chatModel);
    }
}
