package com.devflow.infra.mq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * RabbitMQ 配置
 */
@Configuration
@Profile("!test")
public class RabbitMqConfig {

    // 队列定义
    public static final String TASK_QUEUE = "devflow.task.queue";
    public static final String TASK_EXCHANGE = "devflow.task.exchange";
    public static final String TASK_ROUTING_KEY = "devflow.task.routing";

    // 审查队列（用于并行审查）
    public static final String REVIEW_QUEUE = "devflow.review.queue";
    public static final String REVIEW_EXCHANGE = "devflow.review.exchange";
    public static final String REVIEW_ROUTING_KEY = "devflow.review.routing";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setPrefetchCount(1);
        // AcknowledgeMode defaults to AUTO, which is the expected behavior
        return factory;
    }

    // Task Queue
    @Bean
    public Queue taskQueue() {
        return QueueBuilder.durable(TASK_QUEUE).build();
    }

    @Bean
    public DirectExchange taskExchange() {
        return new DirectExchange(TASK_EXCHANGE);
    }

    @Bean
    public Binding taskBinding() {
        return BindingBuilder.bind(taskQueue()).to(taskExchange()).with(TASK_ROUTING_KEY);
    }

    // Review Queue
    @Bean
    public Queue reviewQueue() {
        return QueueBuilder.durable(REVIEW_QUEUE).build();
    }

    @Bean
    public DirectExchange reviewExchange() {
        return new DirectExchange(REVIEW_EXCHANGE);
    }

    @Bean
    public Binding reviewBinding() {
        return BindingBuilder.bind(reviewQueue()).to(reviewExchange()).with(REVIEW_ROUTING_KEY);
    }
}
