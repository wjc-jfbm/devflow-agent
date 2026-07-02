package com.devflow.api.service;

import com.devflow.api.dto.TaskCreateRequest;
import com.devflow.api.dto.TaskProgressVO;
import com.devflow.common.enums.TaskStatus;
import com.devflow.common.enums.WorkflowPhase;
import com.devflow.common.exception.BusinessException;
import com.devflow.core.orchestration.PipelineOrchestrator;
import com.devflow.infra.mq.TaskProducer;
import com.devflow.infra.persistence.entity.AgentExecution;
import com.devflow.infra.persistence.entity.Task;
import com.devflow.infra.persistence.repository.AgentExecutionRepository;
import com.devflow.infra.persistence.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final AgentExecutionRepository agentExecutionRepository;
    private final TaskProducer taskProducer;
    private final PipelineOrchestrator pipelineOrchestrator;

    @Transactional(rollbackFor = Exception.class)
    public Task createAndStartTask(TaskCreateRequest request) {
        log.info("Creating task: issue#{} - {}", request.getIssueNumber(), request.getIssueTitle());

        Task task = new Task();
        task.setProjectId(request.getProjectId());
        task.setIssueUrl(request.getIssueUrl());
        task.setIssueNumber(request.getIssueNumber());
        task.setIssueTitle(request.getIssueTitle());
        task.setIssueBody(request.getIssueBody());
        task.setStatus(TaskStatus.PENDING.getCode());
        task.setCurrentPhase(WorkflowPhase.INIT.getCode());
        task.setPriority(5);

        taskRepository.save(task);
        log.info("Task created successfully: taskId={}", task.getId());

        // 异步发送到 RabbitMQ，由 TaskConsumer 消费并触发工作流
        // 解决 GitHub Webhook 10s 超时限制：webhook 请求立即返回，
        // 耗时的 AI 流水线在后台异步执行
        taskProducer.sendTask(task.getId());
        log.info("Task message sent to MQ: taskId={}", task.getId());

        return task;
    }

    public Task getTask(Long id) {
        log.debug("Getting task: id={}", id);
        Task task = taskRepository.getById(id);
        if (task == null) {
            log.warn("Task not found: id={}", id);
            throw new BusinessException(404, "Task not found: " + id);
        }
        return task;
    }

    public List<Task> listTasks(Long projectId) {
        log.debug("Listing tasks: projectId={}", projectId);
        if (projectId != null) {
            return taskRepository.findByProjectId(projectId);
        }
        return taskRepository.list();
    }

    public TaskProgressVO getTaskProgress(Long taskId) {
        log.debug("Getting task progress: taskId={}", taskId);
        
        Task task = taskRepository.getById(taskId);
        if (task == null) {
            log.warn("Task not found for progress query: taskId={}", taskId);
            throw new BusinessException(404, "Task not found: " + taskId);
        }

        List<AgentExecution> executions = agentExecutionRepository.findByTaskId(taskId);
        int totalTokens = executions.stream().mapToInt(AgentExecution::getTokensUsed).sum();
        long totalDuration = executions.stream().mapToLong(AgentExecution::getDurationMs).sum();

        return TaskProgressVO.builder()
                .taskId(task.getId())
                .issueTitle(task.getIssueTitle())
                .currentPhase(task.getCurrentPhase())
                .status(task.getStatus())
                .prUrl(task.getPrUrl())
                .totalTokens(totalTokens)
                .totalDurationMs(totalDuration)
                .build();
    }

    public void approveTask(Long taskId, String approver, String comment, String action) {
        log.info("Approving task: taskId={}, approver={}, action={}", taskId, approver, action);

        Task task = taskRepository.getById(taskId);
        if (task == null) {
            throw new BusinessException(404, "Task not found: " + taskId);
        }
        if (!TaskStatus.PAUSED.getCode().equals(task.getStatus())) {
            throw new BusinessException("Task is not in PAUSED status, current status: " + task.getStatus());
        }

        if ("APPROVED".equals(action)) {
            pipelineOrchestrator.approve(taskId, approver, comment);
        } else if ("REJECTED".equals(action)) {
            pipelineOrchestrator.reject(taskId, approver, comment);
        } else {
            throw new BusinessException("Invalid approval action: " + action + ". Must be APPROVED or REJECTED.");
        }

        log.info("Task approval completed: taskId={}, action={}", taskId, action);
    }

    public void deleteTask(Long id) {
        Task task = taskRepository.getById(id);
        if (task == null) {
            throw new BusinessException(404, "Task not found: " + id);
        }
        log.info("Deleting task: id={}, issueTitle={}", id, task.getIssueTitle());
        taskRepository.removeById(id);
    }
}