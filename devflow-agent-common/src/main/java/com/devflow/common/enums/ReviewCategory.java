package com.devflow.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审查维度枚举
 */
@Getter
@AllArgsConstructor
public enum ReviewCategory {

    SECURITY("SECURITY", "安全审查"),
    PERFORMANCE("PERFORMANCE", "性能审查"),
    CONVENTION("CONVENTION", "规范审查");

    private final String code;
    private final String description;
}
