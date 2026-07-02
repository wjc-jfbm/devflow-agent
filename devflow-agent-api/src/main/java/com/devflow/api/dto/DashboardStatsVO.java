package com.devflow.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 仪表盘统计 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsVO {

    private Long totalTasks;
    private Long completedTasks;
    private Long runningTasks;
    private Long failedTasks;
    private Long totalTokensUsed;
    private Long avgDurationMs;
}
