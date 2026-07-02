package com.devflow.core.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 代码库索引器
 * 将目标项目的代码文件索引到向量存储，用于 RAG 检索
 * 当前简化实现：使用 Redis 缓存代码片段，后续可切换到 pgvector
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodebaseIndexer {

    private static final String INDEX_KEY = "rag:project:%d:index";
    private static final long INDEX_TTL_HOURS = 24;

    private final StringRedisTemplate redisTemplate;

    /**
     * 索引代码文件
     */
    public void indexFile(Long projectId, String filePath, String content) {
        String key = String.format(INDEX_KEY, projectId);
        Map<String, String> chunk = new HashMap<>();
        chunk.put("path", filePath);
        chunk.put("content", content);
        chunk.put("timestamp", String.valueOf(System.currentTimeMillis()));

        redisTemplate.opsForHash().put(key, filePath, content);
        redisTemplate.expire(key, INDEX_TTL_HOURS, TimeUnit.HOURS);
        log.debug("Indexed file: project={}, path={}", projectId, filePath);
    }

    /**
     * 批量索引
     */
    public void indexFiles(Long projectId, Map<String, String> files) {
        String key = String.format(INDEX_KEY, projectId);
        redisTemplate.opsForHash().putAll(key, files);
        redisTemplate.expire(key, INDEX_TTL_HOURS, TimeUnit.HOURS);
        log.info("Indexed {} files for project {}", files.size(), projectId);
    }

    /**
     * 清除索引
     */
    public void clearIndex(Long projectId) {
        String key = String.format(INDEX_KEY, projectId);
        redisTemplate.delete(key);
        log.info("Index cleared for project {}", projectId);
    }
}
