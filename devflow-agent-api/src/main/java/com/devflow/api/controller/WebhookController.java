package com.devflow.api.controller;

import com.devflow.api.dto.TaskCreateRequest;
import com.devflow.api.service.TaskService;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.model.R;
import com.devflow.infra.persistence.entity.Project;
import com.devflow.infra.persistence.entity.Task;
import com.devflow.infra.persistence.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * GitHub Webhook 控制器
 * 接收 GitHub 事件通知，触发工作流
 */
@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final TaskService taskService;
    private final ProjectRepository projectRepository;

    @Value("${github.webhook.secret}")
    private String webhookSecret;

    /**
     * 接收 GitHub Webhook 事件
     */
    @PostMapping("/github")
    public R<Void> handleGitHubWebhook(
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] rawPayload) {

        log.info("Received GitHub webhook event: {}", event);

        // 验证 Webhook 签名
        if (webhookSecret != null && !webhookSecret.isEmpty()) {
            if (signature == null || !verifySignature(rawPayload, signature)) {
                log.warn("Invalid webhook signature");
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid signature");
            }
        }

        String payloadStr = new String(rawPayload, StandardCharsets.UTF_8);

        if (!"issues".equals(event)) {
            log.debug("Ignoring non-issue event: {}", event);
            return R.ok();
        }

        // 解析 payload (使用 JsonUtils 或手动解析)
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = com.devflow.common.utils.JsonUtils.fromJson(
                payloadStr, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

        // 解析 Issue 事件
        String action = (String) payload.get("action");
        if (!"opened".equals(action) && !"edited".equals(action)) {
            log.debug("Ignoring issue action: {}", action);
            return R.ok();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> issue = (Map<String, Object>) payload.get("issue");
        @SuppressWarnings("unchecked")
        Map<String, Object> repository = (Map<String, Object>) payload.get("repository");

        if (issue == null || repository == null) {
            log.warn("Invalid webhook payload: missing issue or repository");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payload");
        }

        String issueTitle = (String) issue.get("title");
        String issueBody = (String) issue.get("body");
        Object issueNumberObj = issue.get("number");
        Integer issueNumber = issueNumberObj instanceof Integer ? (Integer) issueNumberObj
                : (issueNumberObj instanceof Number ? ((Number) issueNumberObj).intValue() : null);
        String issueUrl = (String) issue.get("html_url");
        String repoUrl = (String) repository.get("html_url");

        log.info("New issue created: #{} - {}", issueNumber, issueTitle);

        // 根据 repoUrl 查找对应的项目
        Project project = findProjectByRepoUrl(repoUrl);
        if (project == null) {
            log.warn("No project found for repo: {}. Create a project in DevFlow first.", repoUrl);
            throw new BusinessException(404, "No project found for repo: " + repoUrl + ". Please create a project first.");
        }

        // 创建任务并触发工作流
        TaskCreateRequest request = new TaskCreateRequest();
        request.setProjectId(project.getId());
        request.setIssueTitle(issueTitle);
        request.setIssueBody(issueBody);
        request.setIssueNumber(issueNumber);
        request.setIssueUrl(issueUrl);

        Task task = taskService.createAndStartTask(request);
        log.info("Task created and workflow started: taskId={}, projectId={}", task.getId(), project.getId());

        return R.ok();
    }

    /**
     * 验证 GitHub Webhook HMAC-SHA256 签名（恒定时间比较防止时序攻击）
     */
    private boolean verifySignature(byte[] payload, String signatureHeader) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] computedHash = mac.doFinal(payload);
            String computedSignature = "sha256=" + HexFormat.of().formatHex(computedHash);
            // 使用 MessageDigest.isEqual 进行恒定时间比较，防止时序攻击
            return MessageDigest.isEqual(
                    computedSignature.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to verify webhook signature", e);
            return false;
        }
    }

    /**
     * 根据仓库 URL 查找项目（委托给 Repository 的数据库查询）
     */
    private Project findProjectByRepoUrl(String repoUrl) {
        return projectRepository.findByRepoUrl(repoUrl);
    }
}
