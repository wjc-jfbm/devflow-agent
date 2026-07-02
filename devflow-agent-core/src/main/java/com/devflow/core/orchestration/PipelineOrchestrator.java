package com.devflow.core.orchestration;

import com.devflow.common.enums.TaskStatus;
import com.devflow.common.enums.WorkflowPhase;
import com.devflow.common.exception.BusinessException;
import com.devflow.infra.persistence.entity.Approval;
import com.devflow.infra.persistence.entity.Task;
import com.devflow.infra.persistence.mapper.ApprovalMapper;
import com.devflow.infra.persistence.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineOrchestrator {

    private final WorkflowEngine workflowEngine;
    private final TaskRepository taskRepository;
    private final ApprovalMapper approvalMapper;

    public void triggerPipeline(Task task) {
        log.info("Pipeline triggered: taskId={}, issue={}", task.getId(), task.getIssueTitle());
        
        task.setStatus(TaskStatus.RUNNING.getCode());
        task.setCurrentPhase(WorkflowPhase.INIT.getCode());
        taskRepository.updateById(task);
        
        workflowEngine.startWorkflow(task.getId());
        log.info("Workflow started: taskId={}", task.getId());
    }

    public void approve(Long taskId, String approver, String comment) {
        Task task = getTaskOrThrow(taskId);
        
        log.info("Approving task: taskId={}, phase={}, approver={}", taskId, task.getCurrentPhase(), approver);
        
        saveApproval(taskId, task.getCurrentPhase(), "APPROVED", approver, comment);
        
        workflowEngine.resumeWorkflow(taskId);
        log.info("Task approved, workflow resumed: taskId={}", taskId);
    }

    public void reject(Long taskId, String approver, String comment) {
        Task task = getTaskOrThrow(taskId);
        
        log.info("Rejecting task: taskId={}, phase={}, approver={}", taskId, task.getCurrentPhase(), approver);
        
        saveApproval(taskId, task.getCurrentPhase(), "REJECTED", approver, comment);
        
        task.setStatus(TaskStatus.FAILED.getCode());
        task.setErrorMsg("Rejected at phase " + task.getCurrentPhase() + ": " + comment);
        taskRepository.updateById(task);
        
        log.info("Task rejected and marked as failed: taskId={}", taskId);
    }

    private Task getTaskOrThrow(Long taskId) {
        Task task = taskRepository.getById(taskId);
        if (task == null) {
            throw new BusinessException(404, "Task not found: " + taskId);
        }
        return task;
    }

    private void saveApproval(Long taskId, String phase, String status, String approver, String comment) {
        Approval approval = new Approval();
        approval.setTaskId(taskId);
        approval.setPhase(phase);
        approval.setStatus(status);
        approval.setApprover(approver);
        approval.setComment(comment);
        approvalMapper.insert(approval);
    }
}