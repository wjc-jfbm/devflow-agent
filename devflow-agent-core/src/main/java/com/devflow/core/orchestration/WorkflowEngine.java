package com.devflow.core.orchestration;

import com.devflow.common.enums.TaskStatus;
import com.devflow.common.enums.WorkflowPhase;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.utils.JsonUtils;
import com.devflow.core.agent.*;
import com.devflow.core.tool.GitHubTools;
import com.devflow.infra.persistence.entity.AgentExecution;
import com.devflow.infra.persistence.entity.Task;
import com.devflow.infra.persistence.repository.AgentExecutionRepository;
import com.devflow.infra.persistence.repository.TaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class WorkflowEngine {

    private static final String WORKFLOW_STATE_KEY = "workflow:%d:state";
    private static final long WORKFLOW_STATE_TTL_HOURS = 24;
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile(
            "```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final RequirementsAgent requirementsAgent;
    private final ArchitectAgent architectAgent;
    private final CoderAgent coderAgent;
    private final TesterAgent testerAgent;
    private final ReviewerAgent reviewerAgent;
    private final SupervisorAgent supervisorAgent;
    private final GitHubTools gitHubTools;
    private final TaskRepository taskRepository;
    private final AgentExecutionRepository agentExecutionRepository;
    private final StringRedisTemplate redisTemplate;

    private final int maxRetries;
    private final boolean parallelReview;
    private final ExecutorService reviewExecutor = Executors.newFixedThreadPool(3);

    public WorkflowEngine(RequirementsAgent requirementsAgent, ArchitectAgent architectAgent,
                          CoderAgent coderAgent, TesterAgent testerAgent, ReviewerAgent reviewerAgent,
                          SupervisorAgent supervisorAgent, GitHubTools gitHubTools,
                          TaskRepository taskRepository, AgentExecutionRepository agentExecutionRepository,
                          StringRedisTemplate redisTemplate,
                          @Value("${devflow.agent.max-retries:3}") int maxRetries,
                          @Value("${devflow.agent.parallel-review:true}") boolean parallelReview) {
        this.requirementsAgent = requirementsAgent;
        this.architectAgent = architectAgent;
        this.coderAgent = coderAgent;
        this.testerAgent = testerAgent;
        this.reviewerAgent = reviewerAgent;
        this.supervisorAgent = supervisorAgent;
        this.gitHubTools = gitHubTools;
        this.taskRepository = taskRepository;
        this.agentExecutionRepository = agentExecutionRepository;
        this.redisTemplate = redisTemplate;
        this.maxRetries = maxRetries;
        this.parallelReview = parallelReview;
    }

    // === Public API ===

    @Transactional(rollbackFor = Exception.class)
    public void startWorkflow(Long taskId) {
        Task task = getTaskOrThrow(taskId);
        log.info("Starting workflow: taskId={}, issue={}", taskId, task.getIssueTitle());

        updateTaskPhase(task, WorkflowPhase.INIT);
        saveWorkflowState(taskId, WorkflowPhase.INIT);

        try {
            // 使用 SupervisorAgent 判断工作流类型
            String routeDecision = supervisorAgent.analyzeAndRoute(task);
            String workflowType = parseWorkflowType(routeDecision);
            log.info("Supervisor decided workflow type: {} for taskId={}", workflowType, taskId);

            executePipeline(task, workflowType);
        } catch (Exception e) {
            log.error("Workflow failed: taskId={}", taskId, e);
            updateTaskStatus(task, TaskStatus.FAILED, e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void resumeWorkflow(Long taskId) {
        Task task = getTaskOrThrow(taskId);
        WorkflowPhase currentPhase = WorkflowPhase.fromCode(task.getCurrentPhase());
        String workflowType = getCachedResult(taskId, "workflowType");
        if (workflowType == null) {
            workflowType = "FULL";
        }
        log.info("Resuming workflow: taskId={}, phase={}, type={}", taskId, currentPhase, workflowType);

        try {
            executePipelineFrom(task, currentPhase.next(), workflowType);
        } catch (Exception e) {
            log.error("Workflow resume failed: taskId={}", taskId, e);
            updateTaskStatus(task, TaskStatus.FAILED, e.getMessage());
        }
    }

    // === Pipeline Execution ===

    private void executePipeline(Task task, String workflowType) {
        cacheResult(task.getId(), "workflowType", workflowType);
        executePipelineFrom(task, WorkflowPhase.REQUIREMENTS, workflowType);
    }

    private void executePipelineFrom(Task task, WorkflowPhase startPhase, String workflowType) {
        updateTaskStatus(task, TaskStatus.RUNNING, null);

        boolean isFull = "FULL".equals(workflowType);
        boolean isQuick = "QUICK".equals(workflowType);
        boolean isReview = "REVIEW".equals(workflowType);

        // REQUIREMENTS phase — only for FULL
        if (isFull && shouldExecute(startPhase, WorkflowPhase.REQUIREMENTS)) {
            executeRequirementsPhase(task);
        }

        // ARCHITECT phase — only for FULL
        if (isFull && shouldExecute(startPhase, WorkflowPhase.ARCHITECT)) {
            executeArchitectPhase(task);
        }

        // APPROVAL_ARCHITECT — only for FULL
        if (isFull && shouldExecute(startPhase, WorkflowPhase.APPROVAL_ARCHITECT)) {
            pauseForApproval(task, WorkflowPhase.APPROVAL_ARCHITECT);
            return;
        }

        // CODING phase — FULL, QUICK, SIMPLE (skip for REVIEW-only)
        if (!isReview && shouldExecute(startPhase, WorkflowPhase.CODING)) {
            executeCodingPhase(task);
        }

        // TESTING phase — FULL, QUICK (skip for SIMPLE and REVIEW)
        if ((isFull || isQuick) && shouldExecute(startPhase, WorkflowPhase.TESTING)) {
            executeTestingPhase(task);
        }

        // REVIEW phase — all types
        if (shouldExecute(startPhase, WorkflowPhase.REVIEW)) {
            executeReviewPhase(task);
        }

        // APPROVAL_REVIEW — FULL, QUICK (pause for human review approval)
        if ((isFull || isQuick) && shouldExecute(startPhase, WorkflowPhase.APPROVAL_REVIEW)) {
            pauseForApproval(task, WorkflowPhase.APPROVAL_REVIEW);
            return;
        }

        // PR_CREATION — all except REVIEW-only (review-only doesn't create PR)
        if (!isReview && shouldExecute(startPhase, WorkflowPhase.PR_CREATION)) {
            executePrCreationPhase(task);
        }

        completeWorkflow(task);
    }

    private boolean shouldExecute(WorkflowPhase startPhase, WorkflowPhase target) {
        return startPhase.ordinal() <= target.ordinal();
    }

    // === Phase Implementations ===

    private void executeRequirementsPhase(Task task) {
        log.info("Executing REQUIREMENTS phase: taskId={}", task.getId());
        String result = executeAgentWithRetry(task, "REQUIREMENTS",
                () -> requirementsAgent.analyze(buildIssueContent(task)));
        updateTaskPhase(task, WorkflowPhase.REQUIREMENTS);
        saveWorkflowState(task.getId(), WorkflowPhase.REQUIREMENTS);
        cacheResult(task.getId(), "requirements", cleanJsonOutput(result));
        log.info("REQUIREMENTS phase completed: taskId={}", task.getId());
    }

    private void executeArchitectPhase(Task task) {
        log.info("Executing ARCHITECT phase: taskId={}", task.getId());
        String codeContext = gitHubTools.fetchProjectStructure(task.getProjectId());
        String requirements = getCachedResult(task.getId(), "requirements");
        String result = executeAgentWithRetry(task, "ARCHITECT",
                () -> architectAgent.design(requirements, codeContext));
        updateTaskPhase(task, WorkflowPhase.ARCHITECT);
        saveWorkflowState(task.getId(), WorkflowPhase.ARCHITECT);
        cacheResult(task.getId(), "architect", cleanJsonOutput(result));
        log.info("ARCHITECT phase completed: taskId={}", task.getId());
    }

    private void executeCodingPhase(Task task) {
        log.info("Executing CODING phase: taskId={}", task.getId());
        String architectPlan = getCachedResult(task.getId(), "architect");
        String codeStyleContext = gitHubTools.fetchCodeStyle(task.getProjectId());
        String result = executeAgentWithRetry(task, "CODER",
                () -> coderAgent.implement(architectPlan, codeStyleContext));
        updateTaskPhase(task, WorkflowPhase.CODING);
        saveWorkflowState(task.getId(), WorkflowPhase.CODING);
        cacheResult(task.getId(), "code", cleanJsonOutput(result));
        log.info("CODING phase completed: taskId={}", task.getId());
    }

    private void executeTestingPhase(Task task) {
        log.info("Executing TESTING phase: taskId={}", task.getId());
        String codeChanges = getCachedResult(task.getId(), "code");
        String requirements = getCachedResult(task.getId(), "requirements");
        String result = executeAgentWithRetry(task, "TESTER",
                () -> testerAgent.generateTests(codeChanges, requirements));
        updateTaskPhase(task, WorkflowPhase.TESTING);
        saveWorkflowState(task.getId(), WorkflowPhase.TESTING);
        cacheResult(task.getId(), "test", cleanJsonOutput(result));
        log.info("TESTING phase completed: taskId={}", task.getId());
    }

    private void executeReviewPhase(Task task) {
        log.info("Executing REVIEW phase (parallel={}): taskId={}", parallelReview, task.getId());
        String codeChanges = getCachedResult(task.getId(), "code");
        String testCode = getCachedResult(task.getId(), "test");
        String codeWithTests = codeChanges + "\n\n=== Test Code ===\n" + testCode;

        List<String> reviewCategories = List.of("SECURITY", "PERFORMANCE", "CONVENTION");
        List<String> reviewResults;

        if (parallelReview) {
            // 真正并行执行 3 个审查维度
            List<Future<String>> futures = reviewCategories.stream()
                    .map(category -> reviewExecutor.submit(() ->
                            executeAgentWithRetry(task, "REVIEWER",
                                    () -> reviewerAgent.review(category, codeWithTests))))
                    .toList();
            reviewResults = new ArrayList<>();
            for (Future<String> future : futures) {
                try {
                    reviewResults.add(cleanJsonOutput(future.get(120, TimeUnit.SECONDS)));
                } catch (TimeoutException e) {
                    log.error("Review category timed out", e);
                    reviewResults.add("{\"category\":\"UNKNOWN\",\"score\":0,\"passed\":false,\"issues\":[],\"summary\":\"Review timed out\"}");
                } catch (Exception e) {
                    log.error("Review category failed", e);
                    reviewResults.add("{\"category\":\"UNKNOWN\",\"score\":0,\"passed\":false,\"issues\":[],\"summary\":\"Review failed: " + e.getMessage() + "\"}");
                }
            }
        } else {
            // 串行执行（fallback）
            reviewResults = new ArrayList<>();
            for (String category : reviewCategories) {
                String result = executeAgentWithRetry(task, "REVIEWER",
                        () -> reviewerAgent.review(category, codeWithTests));
                reviewResults.add(cleanJsonOutput(result));
            }
        }

        updateTaskPhase(task, WorkflowPhase.REVIEW);
        saveWorkflowState(task.getId(), WorkflowPhase.REVIEW);
        cacheResult(task.getId(), "review", String.join("\n---\n", reviewResults));
        log.info("REVIEW phase completed: taskId={}", task.getId());
    }

    private void executePrCreationPhase(Task task) {
        log.info("Executing PR_CREATION phase: taskId={}", task.getId());
        String codeChanges = getCachedResult(task.getId(), "code");
        String testCode = getCachedResult(task.getId(), "test");
        String reviewReport = getCachedResult(task.getId(), "review");
        String prUrl = gitHubTools.createPullRequest(task, codeChanges, testCode, reviewReport);
        task.setPrUrl(prUrl);
        updateTaskPhase(task, WorkflowPhase.PR_CREATION);
        saveWorkflowState(task.getId(), WorkflowPhase.PR_CREATION);
        log.info("PR_CREATION phase completed: taskId={}, prUrl={}", task.getId(), prUrl);
    }

    // === Approval ===

    private void pauseForApproval(Task task, WorkflowPhase approvalPhase) {
        updateTaskPhase(task, approvalPhase);
        saveWorkflowState(task.getId(), approvalPhase);
        updateTaskStatus(task, TaskStatus.PAUSED, null);
        log.info("Workflow paused for approval: taskId={}, phase={}", task.getId(), approvalPhase);
    }

    private void completeWorkflow(Task task) {
        updateTaskPhase(task, WorkflowPhase.DONE);
        updateTaskStatus(task, TaskStatus.COMPLETED, null);
        clearAllCachedResults(task.getId());
        log.info("Workflow completed successfully: taskId={}", task.getId());
    }

    // === Agent Execution with Retry ===

    /**
     * 执行 Agent 调用，失败时自动重试（最多 maxRetries 次）
     */
    private String executeAgentWithRetry(Task task, String agentType, Supplier<String> call) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 1) {
                    log.info("Retry {} of {} for agent {}, taskId={}", attempt, maxRetries, agentType, task.getId());
                    // 退避等待：2^attempt 秒
                    Thread.sleep((long) Math.pow(2, attempt) * 1000L);
                }
                return executeAgent(task, agentType, call);
            } catch (Exception e) {
                lastException = e;
                log.warn("Agent {} attempt {}/{} failed: taskId={}, error={}",
                        agentType, attempt, maxRetries, task.getId(), e.getMessage());
            }
        }
        throw new BusinessException("Agent " + agentType + " failed after " + maxRetries + " attempts: "
                + (lastException != null ? lastException.getMessage() : "unknown error"));
    }

    private String executeAgent(Task task, String agentType, Supplier<String> call) {
        long startTime = System.currentTimeMillis();
        AgentExecution execution = new AgentExecution();
        execution.setTaskId(task.getId());
        execution.setAgentType(agentType);
        execution.setInput(agentType + " agent input for task #" + task.getId());
        execution.setStatus("RUNNING");
        agentExecutionRepository.save(execution);

        try {
            String result = call.get();
            long duration = System.currentTimeMillis() - startTime;
            execution.setOutput(result);
            execution.setStatus("COMPLETED");
            execution.setDurationMs(duration);
            execution.setTokensUsed(estimateTokens(result));
            agentExecutionRepository.updateById(execution);
            log.debug("Agent {} completed: taskId={}, duration={}ms", agentType, task.getId(), duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            execution.setStatus("FAILED");
            execution.setDurationMs(duration);
            execution.setErrorMsg(e.getMessage());
            agentExecutionRepository.updateById(execution);
            throw e;
        }
    }

    // === JSON Utilities ===

    /**
     * 清理 LLM 返回的 JSON 字符串：
     * 1. 提取 markdown 代码块中的 JSON
     * 2. 跳过非 JSON 前缀文本
     * 3. 清理尾部非 JSON 内容
     */
    static String cleanJsonOutput(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();

        // 提取 markdown json 代码块
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            trimmed = matcher.group(1).trim();
        }

        // 如果字符串不以 { 或 [ 开头，尝试找到 JSON 起始位置
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            int jsonStart = Math.max(
                    trimmed.indexOf('{'),
                    trimmed.indexOf('['));
            if (jsonStart >= 0) {
                trimmed = trimmed.substring(jsonStart);
            }
        }

        // 查找 JSON 的结束位置（最后一个合法 JSON 结束符）
        if (trimmed.startsWith("{")) {
            int lastBrace = trimmed.lastIndexOf('}');
            if (lastBrace >= 0 && lastBrace < trimmed.length() - 1) {
                trimmed = trimmed.substring(0, lastBrace + 1);
            }
        } else if (trimmed.startsWith("[")) {
            int lastBracket = trimmed.lastIndexOf(']');
            if (lastBracket >= 0 && lastBracket < trimmed.length() - 1) {
                trimmed = trimmed.substring(0, lastBracket + 1);
            }
        }

        return trimmed;
    }

    /**
     * 解析 SupervisorAgent 返回的 JSON 中的 workflowType 字段
     */
    static String parseWorkflowType(String decisionJson) {
        try {
            String cleaned = cleanJsonOutput(decisionJson);
            Map<String, Object> map = JsonUtils.fromJson(cleaned,
                    new TypeReference<Map<String, Object>>() {});
            String type = (String) map.get("workflowType");
            if (type != null && List.of("FULL", "QUICK", "SIMPLE", "REVIEW").contains(type.toUpperCase())) {
                return type.toUpperCase();
            }
        } catch (Exception e) {
            log.warn("Failed to parse supervisor decision, defaulting to FULL: {}", decisionJson);
        }
        return "FULL"; // fallback: 完整流水线
    }

    // === Helpers ===

    private Task getTaskOrThrow(Long taskId) {
        Task task = taskRepository.getById(taskId);
        if (task == null) {
            throw new BusinessException(404, "Task not found: " + taskId);
        }
        return task;
    }

    private void updateTaskPhase(Task task, WorkflowPhase phase) {
        task.setCurrentPhase(phase.getCode());
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.updateById(task);
    }

    private void updateTaskStatus(Task task, TaskStatus status, String errorMsg) {
        task.setStatus(status.getCode());
        if (errorMsg != null) {
            task.setErrorMsg(errorMsg);
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.updateById(task);
    }

    private void saveWorkflowState(Long taskId, WorkflowPhase phase) {
        String key = String.format(WORKFLOW_STATE_KEY, taskId);
        redisTemplate.opsForValue().set(key, phase.getCode(), WORKFLOW_STATE_TTL_HOURS, TimeUnit.HOURS);
    }

    private String buildIssueContent(Task task) {
        String body = task.getIssueBody() != null ? task.getIssueBody() : "";
        return "## Issue #" + task.getIssueNumber() + ": " + task.getIssueTitle() + "\n\n" + body;
    }

    private int estimateTokens(String text) {
        return text == null ? 0 : text.length() / 4;
    }

    private void cacheResult(Long taskId, String key, String value) {
        redisTemplate.opsForValue().set("task:" + taskId + ":" + key, value, 6, TimeUnit.HOURS);
    }

    private String getCachedResult(Long taskId, String key) {
        return redisTemplate.opsForValue().get("task:" + taskId + ":" + key);
    }

    private void clearAllCachedResults(Long taskId) {
        var keys = redisTemplate.keys("task:" + taskId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        redisTemplate.delete(String.format(WORKFLOW_STATE_KEY, taskId));
    }
}