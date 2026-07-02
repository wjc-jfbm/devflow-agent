package com.devflow.core.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 对话记忆管理
 * 为每个 Agent 维护对话上下文
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationMemory {

    private static final String CONVERSATION_KEY = "conversation:%d:%s";
    private static final int MAX_MESSAGES = 20;
    private static final long CONVERSATION_TTL_HOURS = 4;

    private final StringRedisTemplate redisTemplate;

    /**
     * 添加对话消息
     */
    public void addMessage(Long taskId, String agentType, String role, String content) {
        String key = String.format(CONVERSATION_KEY, taskId, agentType);
        String message = role + ":" + content;
        redisTemplate.opsForList().rightPush(key, message);
        redisTemplate.expire(key, CONVERSATION_TTL_HOURS, TimeUnit.HOURS);

        // 限制消息数量
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > MAX_MESSAGES) {
            redisTemplate.opsForList().trim(key, size - MAX_MESSAGES, -1);
        }
    }

    /**
     * 获取对话历史
     */
    public List<String> getHistory(Long taskId, String agentType) {
        String key = String.format(CONVERSATION_KEY, taskId, agentType);
        List<String> messages = redisTemplate.opsForList().range(key, 0, -1);
        return messages != null ? messages : new ArrayList<>();
    }

    /**
     * 清除对话历史
     */
    public void clearHistory(Long taskId, String agentType) {
        String key = String.format(CONVERSATION_KEY, taskId, agentType);
        redisTemplate.delete(key);
    }
}
