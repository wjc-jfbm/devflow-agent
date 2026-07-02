package com.devflow.core.mq;

import com.devflow.common.enums.TaskStatus;
import com.devflow.core.orchestration.WorkflowEngine;
import com.devflow.infra.persistence.entity.Task;
import com.devflow.infra.persistence.repository.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskConsumer 单元测试")
class TaskConsumerTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private WorkflowEngine workflowEngine;

    @InjectMocks
    private TaskConsumer taskConsumer;

    @Nested
    @DisplayName("onTaskMessage — 消费任务消息")
    class OnTaskMessage {

        @Test
        @DisplayName("正常消费 — 触发工作流")
        void shouldStartWorkflowWhenTaskExists() {
            Task task = new Task();
            task.setId(1L);
            task.setStatus(TaskStatus.PENDING.getCode());
            when(taskRepository.getById(1L)).thenReturn(task);

            taskConsumer.onTaskMessage(1L);

            verify(workflowEngine, times(1)).startWorkflow(1L);
        }

        @Test
        @DisplayName("任务不存在 — 记录错误但不抛异常")
        void shouldLogErrorWhenTaskNotFound() {
            when(taskRepository.getById(999L)).thenReturn(null);

            // 不应抛异常，方法应优雅处理
            taskConsumer.onTaskMessage(999L);

            verify(workflowEngine, never()).startWorkflow(anyLong());
        }

        @Test
        @DisplayName("工作流执行异常 — 消费者不崩溃")
        void shouldNotCrashWhenWorkflowThrows() {
            Task task = new Task();
            task.setId(1L);
            when(taskRepository.getById(1L)).thenReturn(task);
            doThrow(new RuntimeException("AI API timeout")).when(workflowEngine).startWorkflow(1L);

            // 不应抛异常
            taskConsumer.onTaskMessage(1L);

            verify(workflowEngine, times(1)).startWorkflow(1L);
        }
    }
}