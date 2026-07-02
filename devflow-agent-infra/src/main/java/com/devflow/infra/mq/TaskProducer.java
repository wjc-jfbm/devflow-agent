package com.devflow.infra.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 任务消息生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class TaskProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送任务消息
     */
    public void sendTask(Long taskId) {
        log.info("Sending task message, taskId={}", taskId);
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.TASK_EXCHANGE,
                RabbitMqConfig.TASK_ROUTING_KEY,
                taskId
        );
    }

    /**
     * 发送审查任务消息
     */
    public void sendReviewTask(Long taskId, String reviewCategory) {
        log.info("Sending review task, taskId={}, category={}", taskId, reviewCategory);
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.REVIEW_EXCHANGE,
                RabbitMqConfig.REVIEW_ROUTING_KEY,
                new ReviewTaskMessage(taskId, reviewCategory)
        );
    }

    /**
     * 审查任务消息体
     */
    public record ReviewTaskMessage(Long taskId, String reviewCategory) {}
}
