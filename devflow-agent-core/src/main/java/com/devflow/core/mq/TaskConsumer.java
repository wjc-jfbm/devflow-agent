package com.devflow.core.mq;

import com.devflow.common.enums.TaskStatus;
import com.devflow.core.orchestration.WorkflowEngine;
import com.devflow.infra.mq.RabbitMqConfig;
import com.devflow.infra.persistence.entity.Task;
import com.devflow.infra.persistence.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 任务消息消费者
 * 监听任务队列，触发实际的工作流引擎执行
 * 放在 core 模块以解决循环依赖：api → core → infra → common
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class TaskConsumer {

    private final TaskRepository taskRepository;
    private final WorkflowEngine workflowEngine;

    /**
     * 消费任务消息，触发工作流
     */
    @RabbitListener(queues = RabbitMqConfig.TASK_QUEUE)
    public void onTaskMessage(Long taskId) {
        log.info("Received task message, taskId={}", taskId);
        try {
            Task task = taskRepository.getById(taskId);
            if (task == null) {
                log.error("Task not found for taskId={}, message will be discarded", taskId);
                return;
            }
            // 直接调用工作流引擎执行
            workflowEngine.startWorkflow(taskId);
            log.info("Workflow started for taskId={}, status={}", taskId, task.getStatus());
        } catch (Exception e) {
            log.error("Error processing task message, taskId={}", taskId, e);
            // 尝试将任务标记为失败，避免永久卡在 PENDING 状态
            try {
                Task task = taskRepository.getById(taskId);
                if (task != null) {
                    task.setStatus(TaskStatus.FAILED.getCode());
                    String errMsg = e.getMessage();
                    task.setErrorMsg("Workflow failed: " + (errMsg != null ? errMsg.substring(0, Math.min(errMsg.length(), 500)) : "unknown error"));
                    taskRepository.updateById(task);
                    log.info("Task {} marked as FAILED due to processing error", taskId);
                }
            } catch (Exception updateEx) {
                log.error("CRITICAL: Failed to update task status to FAILED for taskId={}, re-throwing to trigger MQ retry", taskId, updateEx);
                // 重新抛出异常触发 RabbitMQ nack，使消息可以被重新投递
                throw new RuntimeException("Failed to update task status after workflow error for taskId=" + taskId, updateEx);
            }
        }
    }
}
