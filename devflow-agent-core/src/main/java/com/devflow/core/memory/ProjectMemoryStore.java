package com.devflow.core.memory;

import com.devflow.common.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 项目上下文记忆存储
 * 持久化项目级别的上下文信息（架构风格、代码规范、常用模式等）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectMemoryStore {

    private static final String MEMORY_KEY = "memory:project:%d:%s";
    private static final long MEMORY_TTL_HOURS = 72;

    private final StringRedisTemplate redisTemplate;

    /**
     * 保存项目记忆
     */
    public void save(Long projectId, String key, Object value) {
        String redisKey = String.format(MEMORY_KEY, projectId, key);
        String jsonValue = JsonUtils.toJson(value);
        redisTemplate.opsForValue().set(redisKey, jsonValue, MEMORY_TTL_HOURS, TimeUnit.HOURS);
        log.debug("Memory saved: project={}, key={}", projectId, key);
    }

    /**
     * 获取项目记忆
     */
    public <T> T get(Long projectId, String key, Class<T> type) {
        String redisKey = String.format(MEMORY_KEY, projectId, key);
        String jsonValue = redisTemplate.opsForValue().get(redisKey);
        if (jsonValue == null) {
            return null;
        }
        return JsonUtils.fromJson(jsonValue, type);
    }

    /**
     * 获取项目记忆（复杂类型）
     */
    public <T> T get(Long projectId, String key, TypeReference<T> typeRef) {
        String redisKey = String.format(MEMORY_KEY, projectId, key);
        String jsonValue = redisTemplate.opsForValue().get(redisKey);
        if (jsonValue == null) {
            return null;
        }
        return JsonUtils.fromJson(jsonValue, typeRef);
    }

    /**
     * 获取项目所有记忆
     */
    public Map<String, String> getAll(Long projectId) {
        String pattern = String.format(MEMORY_KEY, projectId, "*");
        Map<String, String> result = new HashMap<>();
        var keys = redisTemplate.keys(pattern);
        if (keys != null) {
            for (String key : keys) {
                String value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    /**
     * 清除项目记忆
     */
    public void clear(Long projectId) {
        String pattern = String.format(MEMORY_KEY, projectId, "*");
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        log.info("Memory cleared for project {}", projectId);
    }
}
