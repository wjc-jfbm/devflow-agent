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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService 单元测试")
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private AgentExecutionRepository agentExecutionRepository;

    @Mock
    private TaskProducer taskProducer;

    @Mock
    private PipelineOrchestrator pipelineOrchestrator;

    @InjectMocks
    private TaskService taskService;

    private Task testTask;
    private TaskCreateRequest testRequest;

    @BeforeEach
    void setUp() {
        testTask = new Task();
        testTask.setId(1L);
        testTask.setProjectId(100L);
        testTask.setIssueNumber(1);
        testTask.setIssueTitle("Test Issue");
        testTask.setIssueBody("Test body");
        testTask.setStatus(TaskStatus.PENDING.getCode());
        testTask.setCurrentPhase(WorkflowPhase.INIT.getCode());

        testRequest = new TaskCreateRequest();
        testRequest.setProjectId(100L);
        testRequest.setIssueNumber(1);
        testRequest.setIssueTitle("Test Issue");
        testRequest.setIssueBody("Test body");
    }

    @Test
    @DisplayName("创建任务 - 成功（发送到 RabbitMQ 异步执行）")
    void createAndStartTask_ShouldCreateTaskAndSendToMq() {
        doAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(1L);
            return true;
        }).when(taskRepository).save(any(Task.class));

        Task result = taskService.createAndStartTask(testRequest);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(100L, result.getProjectId());
        assertEquals(TaskStatus.PENDING.getCode(), result.getStatus());
        assertEquals(WorkflowPhase.INIT.getCode(), result.getCurrentPhase());
        // 验证保存到数据库
        verify(taskRepository, times(1)).save(any(Task.class));
        // 验证发送到 RabbitMQ（异步）
        verify(taskProducer, times(1)).sendTask(1L);
        // PipelineOrchestrator 不再由 TaskService 直接调用
        verify(pipelineOrchestrator, never()).triggerPipeline(any(Task.class));
    }

    @Test
    @DisplayName("获取任务详情 - 任务存在")
    void getTask_ShouldReturnTask_WhenTaskExists() {
        when(taskRepository.getById(1L)).thenReturn(testTask);

        Task result = taskService.getTask(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Test Issue", result.getIssueTitle());
        verify(taskRepository, times(1)).getById(1L);
    }

    @Test
    @DisplayName("获取任务详情 - 任务不存在")
    void getTask_ShouldThrowException_WhenTaskNotExists() {
        when(taskRepository.getById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> taskService.getTask(999L));
        verify(taskRepository, times(1)).getById(999L);
    }

    @Test
    @DisplayName("获取任务列表 - 带项目ID过滤")
    void listTasks_ShouldReturnFilteredTasks_WhenProjectIdProvided() {
        List<Task> tasks = Arrays.asList(testTask);
        when(taskRepository.findByProjectId(100L)).thenReturn(tasks);

        List<Task> result = taskService.listTasks(100L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test Issue", result.get(0).getIssueTitle());
        verify(taskRepository, times(1)).findByProjectId(100L);
    }

    @Test
    @DisplayName("获取任务列表 - 不带参数")
    void listTasks_ShouldReturnAllTasks_WhenProjectIdNotProvided() {
        List<Task> tasks = Arrays.asList(testTask);
        when(taskRepository.list()).thenReturn(tasks);

        List<Task> result = taskService.listTasks(null);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(taskRepository, times(1)).list();
    }

    @Test
    @DisplayName("获取任务进度 - 任务存在")
    void getTaskProgress_ShouldReturnProgress_WhenTaskExists() {
        AgentExecution execution = new AgentExecution();
        execution.setTaskId(1L);
        execution.setTokensUsed(100);
        execution.setDurationMs(500L);

        when(taskRepository.getById(1L)).thenReturn(testTask);
        when(agentExecutionRepository.findByTaskId(1L)).thenReturn(Arrays.asList(execution));

        TaskProgressVO result = taskService.getTaskProgress(1L);

        assertNotNull(result);
        assertEquals(1L, result.getTaskId());
        assertEquals("Test Issue", result.getIssueTitle());
        assertEquals(100, result.getTotalTokens());
        assertEquals(500L, result.getTotalDurationMs());
    }

    @Test
    @DisplayName("获取任务进度 - 任务不存在")
    void getTaskProgress_ShouldThrowException_WhenTaskNotExists() {
        when(taskRepository.getById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> taskService.getTaskProgress(999L));
        verify(taskRepository, times(1)).getById(999L);
        verify(agentExecutionRepository, never()).findByTaskId(anyLong());
    }

    @Test
    @DisplayName("审批任务 - 批准（PAUSED状态）")
    void approveTask_ShouldCallApprove_WhenActionApproved() {
        Task pausedTask = new Task();
        pausedTask.setId(1L);
        pausedTask.setStatus(TaskStatus.PAUSED.getCode());
        when(taskRepository.getById(1L)).thenReturn(pausedTask);

        taskService.approveTask(1L, "admin", "Approved", "APPROVED");

        verify(pipelineOrchestrator, times(1)).approve(1L, "admin", "Approved");
        verify(pipelineOrchestrator, never()).reject(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("审批任务 - 拒绝（PAUSED状态）")
    void approveTask_ShouldCallReject_WhenActionRejected() {
        Task pausedTask = new Task();
        pausedTask.setId(1L);
        pausedTask.setStatus(TaskStatus.PAUSED.getCode());
        when(taskRepository.getById(1L)).thenReturn(pausedTask);

        taskService.approveTask(1L, "admin", "Rejected", "REJECTED");

        verify(pipelineOrchestrator, times(1)).reject(1L, "admin", "Rejected");
        verify(pipelineOrchestrator, never()).approve(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("审批任务 - 非PAUSED状态应抛异常")
    void approveTask_ShouldThrowException_WhenTaskNotPaused() {
        Task runningTask = new Task();
        runningTask.setId(1L);
        runningTask.setStatus(TaskStatus.RUNNING.getCode());
        when(taskRepository.getById(1L)).thenReturn(runningTask);

        assertThrows(BusinessException.class, () ->
            taskService.approveTask(1L, "admin", "Approved", "APPROVED"));

        verify(pipelineOrchestrator, never()).approve(anyLong(), anyString(), anyString());
        verify(pipelineOrchestrator, never()).reject(anyLong(), anyString(), anyString());
    }
}