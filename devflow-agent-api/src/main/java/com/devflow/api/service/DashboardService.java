package com.devflow.api.service;

import com.devflow.api.dto.DashboardStatsVO;
import com.devflow.infra.persistence.entity.AgentExecution;
import com.devflow.infra.persistence.entity.Task;
import com.devflow.infra.persistence.repository.AgentExecutionRepository;
import com.devflow.infra.persistence.repository.TaskRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 仪表盘服务
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TaskRepository taskRepository;
    private final AgentExecutionRepository agentExecutionRepository;

    /**
     * 获取统计数据
     */
    public DashboardStatsVO getStats() {
        long totalTasks = taskRepository.count();
        long completedTasks = taskRepository.count(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, "COMPLETED"));
        long runningTasks = taskRepository.count(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, "RUNNING"));
        long failedTasks = taskRepository.count(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, "FAILED"));

        List<AgentExecution> allExecutions = agentExecutionRepository.list();
        long totalTokens = allExecutions.stream().mapToLong(AgentExecution::getTokensUsed).sum();
        long avgDuration = allExecutions.isEmpty() ? 0 :
                (long) allExecutions.stream().mapToLong(AgentExecution::getDurationMs).average().orElse(0);

        return DashboardStatsVO.builder()
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .runningTasks(runningTasks)
                .failedTasks(failedTasks)
                .totalTokensUsed(totalTokens)
                .avgDurationMs(avgDuration)
                .build();
    }
}
