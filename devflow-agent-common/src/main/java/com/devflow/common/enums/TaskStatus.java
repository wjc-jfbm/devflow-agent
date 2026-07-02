package com.devflow.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态枚举
 */
@Getter
@AllArgsConstructor
public enum TaskStatus {

    PENDING("PENDING", "等待执行"),
    RUNNING("RUNNING", "执行中"),
    PAUSED("PAUSED", "暂停(等待审批)"),
    COMPLETED("COMPLETED", "已完成"),
    FAILED("FAILED", "执行失败");

    private final String code;
    private final String description;
}
