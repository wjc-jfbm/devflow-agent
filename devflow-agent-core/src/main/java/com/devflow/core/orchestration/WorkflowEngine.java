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
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流引擎 — 编排 6 个 AI Agent 的流水线执行
 *
 * 高并发设计要点：
 * - MQ 消费者并发 4-10，本引擎本身无状态，天然支持并行
 * - LLM 调用在事务外执行，避免长时间占用 DB 连接
 * - DB 操作使用 REQUIRES_NEW 小事务，独立提交
 * - Redis 分布式锁防止多实例重复处理同一 task
 * - Resilience4j 限流 + 熔断保护 AI API
 */
@Slf4j
@Component
public class WorkflowEngine {

    private static final String WORKFLOW_STATE_KEY = "workflow:%d:state";
    private static final String LOCK_KEY = "lock:task:%d";
    private static final long LOCK_TTL_SECONDS = 300; // 5 分钟
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
    private final ExecutorService reviewExecutor;
    private final ExecutorService asyncLogExecutor;

    private final int maxRetries;
    private final boolean parallelReview;

    /** 当前实例 ID，用于分布式锁标识 */
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    public WorkflowEngine(RequirementsAgent requirementsAgent, ArchitectAgent architectAgent,
                          CoderAgent coderAgent, TesterAgent testerAgent, ReviewerAgent reviewerAgent,
                          SupervisorAgent supervisorAgent, GitHubTools gitHubTools,
                          TaskRepository taskRepository, AgentExecutionRepository agentExecutionRepository,
                          StringRedisTemplate redisTemplate,
                          @Qualifier("reviewExecutor") ExecutorService reviewExecutor,
                          @Qualifier("asyncLogExecutor") ExecutorService asyncLogExecutor,
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
        this.reviewExecutor = reviewExecutor;
        this.asyncLogExecutor = asyncLogExecutor;
        this.maxRetries = maxRetries;
        this.parallelReview = parallelReview;
    }

    // === Public API ===

    /**
     * 启动工作流（由 MQ Consumer 调用）
     *
     * 注意：此方法不加 @Transactional，LLM 调用（可能耗时数分钟）在事务外执行，
     * 避免长时间占用 DB 连接。每个 DB 写操作使用独立的 REQUIRES_NEW 事务。
     */
    public void startWorkflow(Long taskId) {
        // 分布式锁：防止多实例重复处理同一 task
        if (!acquireLock(taskId)) {
            log.info("Task {} is already being processed by another instance, skipping", taskId);
            return;
        }

        Task task = getTaskOrThrow(taskId);
        log.info("Starting workflow: taskId={}, issue={}, instance={}", taskId, task.getIssueTitle(), instanceId);

        updateTaskPhaseInTx(taskId, WorkflowPhase.INIT);
        saveWorkflowState(taskId, WorkflowPhase.INIT);

        try {
            String routeDecision = supervisorAgent.analyzeAndRoute(task);
            String workflowType = parseWorkflowType(routeDecision);
            log.info("Supervisor decided workflow type: {} for taskId={}", workflowType, taskId);

            executePipeline(task, workflowType);
        } catch (Exception e) {
            log.error("Workflow failed: taskId={}", taskId, e);
            updateTaskStatusInTx(taskId, TaskStatus.FAILED, e.getMessage());
        } finally {
            releaseLock(taskId);
        }
    }

    /**
     * 恢复暂停的工作流（审批通过后调用）
     */
    public void resumeWorkflow(Long taskId) {
        Task task = getTaskOrThrow(taskId);
        WorkflowPhase currentPhase = WorkflowPhase.fromCode(task.getCurrentPhase());
        String workflowType = getCachedResult(taskId, "workflowType");
        if (workflowType == null) {
            workflowType = "FULL";
        }
        log.info("Resuming workflow: taskId={}, phase={}, type={}, instance={}",
                taskId, currentPhase, workflowType, instanceId);

        try {
            executePipelineFrom(task, currentPhase.next(), workflowType);
        } catch (Exception e) {
            log.error("Workflow resume failed: taskId={}", taskId, e);
            updateTaskStatusInTx(taskId, TaskStatus.FAILED, e.getMessage());
        }
    }

    // === Distributed Lock ===

    private boolean acquireLock(Long taskId) {
        String key = String.format(LOCK_KEY, taskId);
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(key, instanceId, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(locked);
    }

    private void releaseLock(Long taskId) {
        String key = String.format(LOCK_KEY, taskId);
        // 只释放自己持有的锁（Lua 脚本保证原子性）
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        redisTemplate.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>(script, Long.class),
                List.of(key), instanceId);
    }

    // === Pipeline Execution ===

    private void executePipeline(Task task, String workflowType) {
        cacheResult(task.getId(), "workflowType", workflowType);
        executePipelineFrom(task, WorkflowPhase.REQUIREMENTS, workflowType);
    }

    private void executePipelineFrom(Task task, WorkflowPhase startPhase, String workflowType) {
        updateTaskStatusInTx(task.getId(), TaskStatus.RUNNING, null);

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

        // PR_CREATION — all except REVIEW-only
        if (!isReview && shouldExecute(startPhase, WorkflowPhase.PR_CREATION)) {
            executePrCreationPhase(task);
        }

        completeWorkflow(task);
    }

    private boolean shouldExecute(WorkflowPhase startPhase, WorkflowPhase target) {
        return startPhase.ordinal() <= target.ordinal();
    }

    // === Phase Implementations (LLM calls — NO @Transactional) ===

    private void executeRequirementsPhase(Task task) {
        log.info("Executing REQUIREMENTS phase: taskId={}", task.getId());
        String result = executeAgentWithRetry(task, "REQUIREMENTS",
                () -> requirementsAgent.analyze(buildIssueContent(task)));
        updateTaskPhaseInTx(task.getId(), WorkflowPhase.REQUIREMENTS);
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
        updateTaskPhaseInTx(task.getId(), WorkflowPhase.ARCHITECT);
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
        updateTaskPhaseInTx(task.getId(), WorkflowPhase.CODING);
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
        updateTaskPhaseInTx(task.getId(), WorkflowPhase.TESTING);
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
            // 使用 Spring 管理的线程池并行执行 3 个审查维度
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

        updateTaskPhaseInTx(task.getId(), WorkflowPhase.REVIEW);
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
        updateTaskPrUrlInTx(task.getId(), prUrl);
        updateTaskPhaseInTx(task.getId(), WorkflowPhase.PR_CREATION);
        saveWorkflowState(task.getId(), WorkflowPhase.PR_CREATION);
        log.info("PR_CREATION phase completed: taskId={}, prUrl={}", task.getId(), prUrl);
    }

    // === Approval ===

    private void pauseForApproval(Task task, WorkflowPhase approvalPhase) {
        updateTaskPhaseInTx(task.getId(), approvalPhase);
        saveWorkflowState(task.getId(), approvalPhase);
        updateTaskStatusInTx(task.getId(), TaskStatus.PAUSED, null);
        log.info("Workflow paused for approval: taskId={}, phase={}", task.getId(), approvalPhase);
    }

    private void completeWorkflow(Task task) {
        updateTaskPhaseInTx(task.getId(), WorkflowPhase.DONE);
        updateTaskStatusInTx(task.getId(), TaskStatus.COMPLETED, null);
        clearAllCachedResults(task.getId());
        log.info("Workflow completed successfully: taskId={}", task.getId());
    }

    // === Agent Execution with Retry ===

    /**
     * 执行 Agent 调用，失败时自动重试（最多 maxRetries 次）
     *
     * 注意：LLM 调用不在事务内，重试时的退避等待也不会占用 DB 连接
     */
    private String executeAgentWithRetry(Task task, String agentType, Supplier<String> call) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 1) {
                    log.info("Retry {} of {} for agent {}, taskId={}",
                            attempt, maxRetries, agentType, task.getId());
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

    /**
     * 执行单个 Agent 调用，受 Resilience4j 限流 + 熔断保护
     *
     * rateLimiter: 限制对 AI API 的调用频率，防止触发上游 429
     * circuitBreaker: 当 AI API 连续失败时快速熔断，避免雪崩
     */
    @RateLimiter(name = "deepseek", fallbackMethod = "agentFallback")
    @CircuitBreaker(name = "deepseek", fallbackMethod = "agentFallback")
    public String executeAgent(Task task, String agentType, Supplier<String> call) {
        long startTime = System.currentTimeMillis();
        AgentExecution execution = new AgentExecution();
        execution.setTaskId(task.getId());
        execution.setAgentType(agentType);
        execution.setInput(agentType + " agent input for task #" + task.getId());
        execution.setStatus("RUNNING");
        // 初始 save 保持同步（需要数据库生成的 ID 用于后续 update）
        agentExecutionRepository.save(execution);

        try {
            String result = call.get();
            long duration = System.currentTimeMillis() - startTime;
            execution.setOutput(result);
            execution.setStatus("COMPLETED");
            execution.setDurationMs(duration);
            execution.setTokensUsed(estimateTokens(result));
            // 异步写入完成状态，解耦 LLM 主流程
            asyncLogExecutor.execute(() -> {
                try {
                    agentExecutionRepository.updateById(execution);
                } catch (Exception e) {
                    log.warn("Failed to async-update agent execution status: taskId={}, agentType={}",
                            task.getId(), agentType, e);
                }
            });
            log.debug("Agent {} completed: taskId={}, duration={}ms", agentType, task.getId(), duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            execution.setStatus("FAILED");
            execution.setDurationMs(duration);
            execution.setErrorMsg(e.getMessage());
            // 异步写入失败状态
            asyncLogExecutor.execute(() -> {
                try {
                    agentExecutionRepository.updateById(execution);
                } catch (Exception ex) {
                    log.warn("Failed to async-update agent error status: taskId={}, agentType={}",
                            task.getId(), agentType, ex);
                }
            });
            throw e;
        }
    }

    /**
     * Resilience4j 限流/熔断 fallback — 返回明确的错误而非直接抛异常
     */
    @SuppressWarnings("unused")
    private String agentFallback(Task task, String agentType, Supplier<String> call, Throwable t) {
        log.warn("Agent {} throttled/broken for taskId={}: {}", agentType, task.getId(), t.getMessage());
        throw new BusinessException("AI service temporarily unavailable (rate limited or circuit open). "
                + "Task will be retried. Reason: " + t.getMessage());
    }

    // === Transactional DB Operations (REQUIRES_NEW — 独立事务，立即提交) ===

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateTaskPhaseInTx(Long taskId, WorkflowPhase phase) {
        Task task = taskRepository.getById(taskId);
        if (task != null) {
            task.setCurrentPhase(phase.getCode());
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.updateById(task);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateTaskStatusInTx(Long taskId, TaskStatus status, String errorMsg) {
        Task task = taskRepository.getById(taskId);
        if (task != null) {
            task.setStatus(status.getCode());
            if (errorMsg != null) {
                task.setErrorMsg(errorMsg);
            }
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.updateById(task);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateTaskPrUrlInTx(Long taskId, String prUrl) {
        Task task = taskRepository.getById(taskId);
        if (task != null) {
            task.setPrUrl(prUrl);
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.updateById(task);
        }
    }

    // === Helpers ===

    private Task getTaskOrThrow(Long taskId) {
        Task task = taskRepository.getById(taskId);
        if (task == null) {
            throw new BusinessException(404, "Task not found: " + taskId);
        }
        return task;
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

        Matcher matcher = JSON_BLOCK_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            trimmed = matcher.group(1).trim();
        }

        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            int jsonStart = Math.max(
                    trimmed.indexOf('{'),
                    trimmed.indexOf('['));
            if (jsonStart >= 0) {
                trimmed = trimmed.substring(jsonStart);
            }
        }

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
        return "FULL";
    }
}