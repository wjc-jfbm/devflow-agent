package com.devflow.core.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 代码检索器
 * 从向量存储中检索与查询相关的代码片段
 * 当前简化实现：基于关键词匹配，后续可切换到 pgvector 语义检索
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeRetriever {

    private static final String INDEX_KEY = "rag:project:%d:index";

    private final StringRedisTemplate redisTemplate;

    /**
     * 检索与查询相关的代码片段
     */
    public List<CodeSnippet> retrieve(Long projectId, String query, int topK) {
        String key = String.format(INDEX_KEY, projectId);
        Map<Object, Object> index = redisTemplate.opsForHash().entries(key);

        if (index.isEmpty()) {
            log.warn("No index found for project {}", projectId);
            return Collections.emptyList();
        }

        // 简化实现：基于关键词匹配排序
        List<CodeSnippet> results = new ArrayList<>();
        String[] keywords = query.toLowerCase().split("\\s+");

        for (Map.Entry<Object, Object> entry : index.entrySet()) {
            String filePath = entry.getKey().toString();
            String content = entry.getValue().toString();
            double score = calculateScore(content, keywords);
            if (score > 0) {
                results.add(new CodeSnippet(filePath, content, score));
            }
        }

        results.sort((a, b) -> Double.compare(b.score(), a.score()));
        return results.stream().limit(topK).toList();
    }

    /**
     * 计算关键词匹配得分
     */
    private double calculateScore(String content, String[] keywords) {
        String lowerContent = content.toLowerCase();
        double score = 0;
        for (String keyword : keywords) {
            if (lowerContent.contains(keyword)) {
                score += 1.0;
            }
        }
        return score / keywords.length;
    }

    public record CodeSnippet(String filePath, String content, double score) {}
}
