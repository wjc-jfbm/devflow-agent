package com.devflow.api.service;

import com.devflow.api.dto.DashboardStatsVO;
import com.devflow.infra.persistence.mapper.AgentExecutionMapper;
import com.devflow.infra.persistence.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘服务
 *
 * 性能优化：
 * - 任务状态统计：单次 GROUP BY 替代 4 次独立 COUNT(*)
 * - Agent 执行统计：SQL 聚合函数(COUNT/SUM/AVG) 替代全表 list + 内存流式计算
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TaskMapper taskMapper;
    private final AgentExecutionMapper agentExecutionMapper;

    /**
     * 获取统计数据 — SQL 聚合级别优化，避免 O(N) 全表扫描
     */
    public DashboardStatsVO getStats() {
        // 一次 GROUP BY 获取所有状态的任务计数
        Map<String, Long> statusCounts = buildStatusCountMap(
                taskMapper.countByStatus());

        long totalTasks = 0L;
        for (Long v : statusCounts.values()) {
            if (v != null) totalTasks += v;
        }
        long completedTasks = statusCounts.getOrDefault("COMPLETED", 0L);
        long runningTasks = statusCounts.getOrDefault("RUNNING", 0L);
        long failedTasks = statusCounts.getOrDefault("FAILED", 0L);

        // 一次 SQL 获取 Agent 执行的聚合统计
        Map<String, Object> agentStats = agentExecutionMapper.getAggregatedStats();
        long totalTokens = agentStats.get("totalTokens") instanceof Number n
                ? n.longValue() : 0L;
        long avgDuration = agentStats.get("avgDurationMs") instanceof Number n
                ? n.longValue() : 0L;

        return DashboardStatsVO.builder()
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .runningTasks(runningTasks)
                .failedTasks(failedTasks)
                .totalTokensUsed(totalTokens)
                .avgDurationMs(avgDuration)
                .build();
    }

    /**
     * 将 MyBatis 返回的 List<Map<String, Object>> 转为 Map<status, count>
     */
    private static Map<String, Long> buildStatusCountMap(List<Map<String, Object>> rows) {
        Map<String, Long> map = new java.util.HashMap<>();
        for (Map<String, Object> row : rows) {
            Object status = row.get("status");
            Object cnt = row.get("cnt");
            if (status instanceof String s && cnt instanceof Number n) {
                map.put(s, n.longValue());
            }
        }
        return map;
    }
}
