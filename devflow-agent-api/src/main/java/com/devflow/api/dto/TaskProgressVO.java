package com.devflow.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务进度 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskProgressVO {

    private Long taskId;
    private String issueTitle;
    private String currentPhase;
    private String status;
    private String prUrl;
    private Integer totalTokens;
    private Long totalDurationMs;
}
