package com.devflow.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * DevFlow 类型安全配置属性
 * 替代散落的 @Value 注解
 */
@Data
@Component
@ConfigurationProperties(prefix = "devflow")
public class DevFlowProperties {

    /** 工作目录配置 */
    private Workspace workspace = new Workspace();

    /** 审批配置 */
    private Approval approval = new Approval();

    /** Agent 配置 */
    private Agent agent = new Agent();

    @Data
    public static class Workspace {
        /** 工作目录基础路径 */
        private String basePath = System.getProperty("java.io.tmpdir") + "/devflow-workspace";
    }

    @Data
    public static class Approval {
        /** 是否启用审批 */
        private boolean enabled = true;
        /** 需要审批的阶段 */
        private List<String> phases = List.of("ARCHITECT", "REVIEW");
    }

    @Data
    public static class Agent {
        /** 最大重试次数 */
        private int maxRetries = 3;
        /** 是否启用并行审查 */
        private boolean parallelReview = true;
    }

    /**
     * LangChain4j 配置（嵌套在 devflow 下方便统一管理）
     */
    @Data
    @Component
    @ConfigurationProperties(prefix = "langchain4j.open-ai")
    public static class LangChain4jProperties {
        private String apiKey = "sk-placeholder";
        private String baseUrl = "https://api.deepseek.com";
        private String modelName = "deepseek-chat";
        private double temperature = 0.1;
        private int maxTokens = 4096;
        private Duration timeout = Duration.ofSeconds(120);
    }
}
