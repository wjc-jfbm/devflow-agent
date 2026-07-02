package com.devflow.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审查严重级别
 */
@Getter
@AllArgsConstructor
public enum ReviewSeverity {

    CRITICAL("CRITICAL", "严重-必须修复"),
    WARNING("WARNING", "警告-建议修复"),
    INFO("INFO", "提示-可选修复");

    private final String code;
    private final String description;
}
