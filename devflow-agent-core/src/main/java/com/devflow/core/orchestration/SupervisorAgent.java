package com.devflow.core.orchestration;

import com.devflow.infra.persistence.entity.Task;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 监督者 Agent - 路由决策和任务协调
 * 根据 Issue 类型决定工作流走向
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupervisorAgent {

    private final ChatLanguageModel chatModel;

    private static final String SYSTEM_PROMPT = """
            你是一个工作流监督者，负责分析 GitHub Issue 并决定工作流走向。
            
            你需要判断：
            1. 这是一个新功能需求 → 执行完整流水线（需求→架构→编码→测试→审查→PR）
            2. 这是一个 Bug 修复 → 执行快速流水线（编码→测试→审查→PR）
            3. 这是一个文档/配置变更 → 执行简单流水线（编码→审查→PR）
            4. 这是一个 PR 审查请求 → 只执行代码审查
            
            只输出一个 JSON：
            {"workflowType": "FULL/QUICK/SIMPLE/REVIEW", "reason": "判断理由"}
            """;

    /**
     * 分析 Issue 并决定工作流类型
     */
    public String analyzeAndRoute(Task task) {
        log.info("Supervisor analyzing task, taskId={}", task.getId());

        var messages = List.of(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from("Issue 标题: " + task.getIssueTitle() + "\nIssue 内容: " + task.getIssueBody())
        );

        var response = chatModel.chat(messages);
        String result = response.aiMessage().text();
        log.info("Supervisor decision for taskId={}: {}", task.getId(), result);

        return result;
    }
}
