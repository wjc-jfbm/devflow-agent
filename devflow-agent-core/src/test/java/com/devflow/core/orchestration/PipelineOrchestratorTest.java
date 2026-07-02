package com.devflow.core.orchestration;

import com.devflow.common.enums.TaskStatus;
import com.devflow.common.enums.WorkflowPhase;
import com.devflow.common.exception.BusinessException;
import com.devflow.infra.persistence.entity.Approval;
import com.devflow.infra.persistence.entity.Task;
import com.devflow.infra.persistence.mapper.ApprovalMapper;
import com.devflow.infra.persistence.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PipelineOrchestrator 单元测试")
class PipelineOrchestratorTest {

    @Mock
    private WorkflowEngine workflowEngine;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ApprovalMapper approvalMapper;

    @InjectMocks
    private PipelineOrchestrator pipelineOrchestrator;

    private Task testTask;

    @BeforeEach
    void setUp() {
        testTask = new Task();
        testTask.setId(1L);
        testTask.setProjectId(100L);
        testTask.setIssueTitle("Test Issue");
        testTask.setStatus(TaskStatus.PENDING.getCode());
        testTask.setCurrentPhase(WorkflowPhase.INIT.getCode());
    }

    @Test
    @DisplayName("触发流水线 - 成功")
    void triggerPipeline_ShouldUpdateStatusAndStartWorkflow() {
        when(taskRepository.updateById(any(Task.class))).thenReturn(true);

        pipelineOrchestrator.triggerPipeline(testTask);

        assertEquals(TaskStatus.RUNNING.getCode(), testTask.getStatus());
        assertEquals(WorkflowPhase.INIT.getCode(), testTask.getCurrentPhase());
        verify(taskRepository, times(1)).updateById(testTask);
        verify(workflowEngine, times(1)).startWorkflow(1L);
    }

    @Test
    @DisplayName("审批通过 - 任务存在")
    void approve_ShouldCreateApprovalAndResumeWorkflow() {
        testTask.setCurrentPhase(WorkflowPhase.APPROVAL_ARCHITECT.getCode());
        when(taskRepository.getById(1L)).thenReturn(testTask);
        when(approvalMapper.insert(any(Approval.class))).thenReturn(1);

        pipelineOrchestrator.approve(1L, "admin", "Approved");

        verify(taskRepository, times(1)).getById(1L);
        verify(approvalMapper, times(1)).insert(any(Approval.class));
        verify(workflowEngine, times(1)).resumeWorkflow(1L);
    }

    @Test
    @DisplayName("审批通过 - 任务不存在")
    void approve_ShouldThrowException_WhenTaskNotExists() {
        when(taskRepository.getById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> 
            pipelineOrchestrator.approve(999L, "admin", "Approved"));

        verify(taskRepository, times(1)).getById(999L);
        verify(approvalMapper, never()).insert(any(Approval.class));
        verify(workflowEngine, never()).resumeWorkflow(anyLong());
    }

    @Test
    @DisplayName("审批拒绝 - 任务存在")
    void reject_ShouldCreateApprovalAndSetFailedStatus() {
        testTask.setCurrentPhase(WorkflowPhase.APPROVAL_ARCHITECT.getCode());
        when(taskRepository.getById(1L)).thenReturn(testTask);
        when(approvalMapper.insert(any(Approval.class))).thenReturn(1);
        when(taskRepository.updateById(any(Task.class))).thenReturn(true);

        pipelineOrchestrator.reject(1L, "admin", "Rejected due to design issues");

        assertEquals(TaskStatus.FAILED.getCode(), testTask.getStatus());
        assertTrue(testTask.getErrorMsg().contains("Rejected"));
        verify(taskRepository, times(1)).getById(1L);
        verify(approvalMapper, times(1)).insert(any(Approval.class));
        verify(taskRepository, times(1)).updateById(testTask);
    }

    @Test
    @DisplayName("审批拒绝 - 任务不存在")
    void reject_ShouldThrowException_WhenTaskNotExists() {
        when(taskRepository.getById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> 
            pipelineOrchestrator.reject(999L, "admin", "Rejected"));

        verify(taskRepository, times(1)).getById(999L);
        verify(approvalMapper, never()).insert(any(Approval.class));
    }
}