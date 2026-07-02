package com.devflow.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 类型枚举
 */
@Getter
@AllArgsConstructor
public enum AgentType {

    REQUIREMENTS("REQUIREMENTS", "需求分析师"),
    ARCHITECT("ARCHITECT", "架构设计师"),
    CODER("CODER", "程序员"),
    TESTER("TESTER", "测试工程师"),
    REVIEWER("REVIEWER", "代码审查员"),
    SUPERVISOR("SUPERVISOR", "监督者");

    private final String code;
    private final String description;

    public static AgentType fromCode(String code) {
        for (AgentType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown AgentType code: " + code);
    }
}
